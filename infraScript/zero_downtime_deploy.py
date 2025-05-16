#!/usr/bin/env python3

import os
import requests
import subprocess
import time
from typing import Dict, Optional
import logging

class ServiceManager:
    # 초기화 함수
    def __init__(self, socat_port: int = 8081, sleep_duration: int = 3) -> None:
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler('/dockerProjects/gdgoc-bugburger/deploy.log'),
                logging.StreamHandler()
            ]
        )
        self.logger = logging.getLogger(__name__)

        self.socat_port: int = socat_port
        self.sleep_duration: int = sleep_duration
        self.services: Dict[str, int] = {
            'gdgoc-bugburger_1': 8082,
            'gdgoc-bugburger_2': 8083
        }
        self.current_name: Optional[str] = None
        self.current_port: Optional[int] = None
        self.next_name: Optional[str] = None
        self.next_port: Optional[int] = None

    # 현재 실행 중인 서비스를 찾는 함수
    def _find_current_service(self) -> None:
        # 첫 번째: socat 프로세스를 확인하여 현재 포워딩되는 포트 찾기
        cmd: str = "ps aux | grep 'socat -t0 TCP-LISTEN:8081' | grep -v grep"
        socat_output: str = subprocess.getoutput(cmd)
        self.logger.info(f"Socat process found: {socat_output}")
        
        # 두 번째: 실행 중인 컨테이너 확인
        cmd_containers: str = "docker ps --format '{{.Names}}' | grep gdgoc-bugburger"
        running_containers: str = subprocess.getoutput(cmd_containers)
        self.logger.info(f"Running containers: {running_containers}")
        
        # 포트 포워딩 확인
        if "localhost:8082" in socat_output:
            self.current_name = 'gdgoc-bugburger_1'
            self.current_port = 8082
        elif "localhost:8083" in socat_output:
            self.current_name = 'gdgoc-bugburger_2'
            self.current_port = 8083
        # socat이 실행 중이지 않거나 정보를 찾을 수 없는 경우 실행 중인 컨테이너 확인
        elif 'gdgoc-bugburger_1' in running_containers:
            self.current_name = 'gdgoc-bugburger_1'
            self.current_port = 8082
        elif 'gdgoc-bugburger_2' in running_containers:
            self.current_name = 'gdgoc-bugburger_2'
            self.current_port = 8083
        else:
            # 기본값 설정 - 첫 번째 서비스 선택
            self.current_name = 'gdgoc-bugburger_1'
            self.current_port = 8082
            
        self.logger.info(f"Determined current service: {self.current_name} on port {self.current_port}")

    # 다음에 실행할 서비스를 찾는 함수
    def _find_next_service(self) -> None:
        # 현재 서비스가 1이면 2로, 2이면 1로 변경
        if self.current_name == 'gdgoc-bugburger_1':
            self.next_name = 'gdgoc-bugburger_2'
            self.next_port = 8083
        else:
            self.next_name = 'gdgoc-bugburger_1'
            self.next_port = 8082
            
        self.logger.info(f"Next service will be: {self.next_name} on port {self.next_port}")

    # Docker 컨테이너를 제거하는 함수
    def _remove_container(self, name: str) -> None:
        self.logger.info(f"Removing container: {name}")
        os.system(f"docker stop {name} 2> /dev/null")
        os.system(f"docker rm -f {name} 2> /dev/null")

    # Docker 컨테이너를 실행하는 함수
    def _run_container(self, name: str, port: int) -> None:
        self.logger.info(f"Starting container: {name} on port {port}")
        os.system(
            f"docker run -d --name={name} --restart unless-stopped -p {port}:8080 -e TZ=Asia/Seoul -v /dockerProjects/gdgoc-bugburger/volumes/gen:/gen --pull always ghcr.io/whqtker/gdgoc-bugburger")

    def _switch_port(self) -> None:
        # Socat 포트를 전환하는 함수
        cmd: str = "ps aux | grep 'socat -t0 TCP-LISTEN:8081' | grep -v grep | awk '{print $2}'"
        pid: str = subprocess.getoutput(cmd)
        
        self.logger.info(f"Current socat PID: {pid}")

        if pid:
            self.logger.info(f"Killing socat process: {pid}")
            os.system(f"kill -9 {pid} 2>/dev/null")

        time.sleep(5)
        
        socat_cmd = f"nohup socat -t0 TCP-LISTEN:{self.socat_port},fork,reuseaddr TCP:localhost:{self.next_port} &>/dev/null &"
        self.logger.info(f"Starting new socat process: {socat_cmd}")
        os.system(socat_cmd)

    # 서비스 상태를 확인하는 함수
    def _is_service_up(self, port: int) -> bool:
        url = f"http://127.0.0.1:{port}/actuator/health"
        try:
            self.logger.info(f"Checking health at: {url}")
            response = requests.get(url, timeout=5)
            self.logger.info(f"Response status code: {response.status_code}")

            if response.status_code == 200 and response.json().get('status') == 'UP':
                self.logger.info(f"Service is 'UP' on port {port}")
                return True
            else:
                self.logger.info(f"Service not up yet. Status code: {response.status_code}, content: {response.text[:100]}")
        except requests.RequestException as e:
            self.logger.info(f"Request failed: {str(e)}")
        return False

    # 서비스를 업데이트하는 함수
    def update_service(self) -> None:
        self.logger.info("Starting service update process...")
        self._find_current_service()
        self.logger.info(f"Current service: {self.current_name} on port {self.current_port}")

        self._find_next_service()
        self.logger.info(f"Next service: {self.next_name} on port {self.next_port}")

        self._remove_container(self.next_name)
        self.logger.info(f"Removed container: {self.next_name}")

        self._run_container(self.next_name, self.next_port)
        self.logger.info(f"Started new container: {self.next_name}")

        # 새 서비스가 'UP' 상태가 될 때까지 기다림
        max_attempts = 30  # 최대 대기 시간 설정 (30 * 3초 = 90초)
        attempts = 0
        while not self._is_service_up(self.next_port) and attempts < max_attempts:
            self.logger.info(f"Waiting for {self.next_name} to be 'UP'... Attempt {attempts+1}/{max_attempts}")
            print(f"Waiting for {self.next_name} to be 'UP'... Attempt {attempts+1}/{max_attempts}")
            time.sleep(self.sleep_duration)
            attempts += 1
            
        if attempts >= max_attempts:
            self.logger.error(f"New service {self.next_name} failed to start after {max_attempts} attempts!")
            print(f"ERROR: New service {self.next_name} failed to start!")
            return

        self._switch_port()
        self.logger.info("Switched ports successfully")

        # 이전 컨테이너 정리
        if self.current_name is not None:
            self._remove_container(self.current_name)
            self.logger.info(f"Removed old container: {self.current_name}")

        print("Switched service successfully!")
        self.logger.info("Service update completed successfully!")


if __name__ == "__main__":
    manager = ServiceManager()
    manager.update_service()