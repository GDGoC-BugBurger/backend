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
        # 실행 중인 컨테이너 확인
        cmd_containers: str = "docker ps --format '{{.Names}}' | grep gdgoc-bugburger"
        running_containers: str = subprocess.getoutput(cmd_containers)
        self.logger.info(f"Running containers: {running_containers}")
        
        # socat 프로세스를 확인하여 현재 포워딩되는 포트 찾기
        cmd: str = "ps aux | grep 'socat -t0 TCP-LISTEN:8081' | grep -v grep"
        socat_output: str = subprocess.getoutput(cmd)
        self.logger.info(f"Socat process found: {socat_output}")
        
        # 포트 포워딩 확인
        if "localhost:8082" in socat_output:
            self.current_name = 'gdgoc-bugburger_1'
            self.current_port = 8082
            self.logger.info(f"Found active socat forwarding to port 8082 (gdgoc-bugburger_1)")
        elif "localhost:8083" in socat_output:
            self.current_name = 'gdgoc-bugburger_2'
            self.current_port = 8083
            self.logger.info(f"Found active socat forwarding to port 8083 (gdgoc-bugburger_2)")
        # socat에서 확인할 수 없는 경우 컨테이너 실행 상태로 판단
        elif 'gdgoc-bugburger_1' in running_containers and 'gdgoc-bugburger_2' in running_containers:
            # 양쪽 다 있으면 더 최근에 생성된 것 확인
            cmd_creation_time = "docker inspect --format='{{.Name}} {{.Created}}' $(docker ps -q --filter name=gdgoc-bugburger)"
            creation_times = subprocess.getoutput(cmd_creation_time)
            self.logger.info(f"Container creation times: {creation_times}")
            
            if 'gdgoc-bugburger_2' in creation_times and creation_times.find('gdgoc-bugburger_2') > creation_times.find('gdgoc-bugburger_1'):
                self.current_name = 'gdgoc-bugburger_2'
                self.current_port = 8083
                self.logger.info("Both containers running, gdgoc-bugburger_2 is newer - using it as current")
            else:
                self.current_name = 'gdgoc-bugburger_1'
                self.current_port = 8082
                self.logger.info("Both containers running, gdgoc-bugburger_1 is newer - using it as current")
        elif 'gdgoc-bugburger_1' in running_containers:
            self.current_name = 'gdgoc-bugburger_1'
            self.current_port = 8082
            self.logger.info("Only gdgoc-bugburger_1 is running")
        elif 'gdgoc-bugburger_2' in running_containers:
            self.current_name = 'gdgoc-bugburger_2'
            self.current_port = 8083
            self.logger.info("Only gdgoc-bugburger_2 is running")
        else:
            # 기본값 설정 - 첫 번째 서비스 선택
            self.current_name = 'gdgoc-bugburger_1'
            self.current_port = 8082
            self.logger.info("No containers running, defaulting to gdgoc-bugburger_1")
            
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
        self.logger.info(f"Attempting to remove container: {name}")
        
        # Check if container exists
        cmd_check = f"docker ps -a --format '{{{{.Names}}}}' | grep -w {name}"
        result = subprocess.getoutput(cmd_check)
        
        if name in result:
            self.logger.info(f"Container {name} exists, stopping and removing")
            # Stop container with timeout
            stop_result = subprocess.getoutput(f"docker stop {name}")
            self.logger.info(f"Stop result: {stop_result}")
            
            # Force remove container
            rm_result = subprocess.getoutput(f"docker rm -f {name}")
            self.logger.info(f"Remove result: {rm_result}")
            
            # Verify container was removed
            verify_cmd = f"docker ps -a --format '{{{{.Names}}}}' | grep -w {name}"
            verify_result = subprocess.getoutput(verify_cmd)
            
            if name in verify_result:
                self.logger.error(f"Failed to remove container {name}, it still exists!")
            else:
                self.logger.info(f"Successfully removed container {name}")
        else:
            self.logger.info(f"Container {name} does not exist, no need to remove")

    # Docker 컨테이너를 실행하는 함수
    def _run_container(self, name: str, port: int) -> None:
        self.logger.info(f"Starting container: {name} on port {port}")
        cmd = f"docker run -d --name={name} --restart unless-stopped -p {port}:8080 -e TZ=Asia/Seoul -v /dockerProjects/gdgoc-bugburger/volumes/gen:/gen --pull always ghcr.io/whqtker/gdgoc-bugburger"
        result = subprocess.getoutput(cmd)
        self.logger.info(f"Container start result: {result}")
        
        # Verify container is running
        verify_cmd = f"docker ps --format '{{{{.Names}}}}' | grep -w {name}"
        verify_result = subprocess.getoutput(verify_cmd)
        
        if name in verify_result:
            self.logger.info(f"Container {name} started successfully")
        else:
            self.logger.error(f"Failed to start container {name}!")

    def _switch_port(self) -> None:
        # Socat 포트를 전환하는 함수
        cmd: str = "ps aux | grep 'socat -t0 TCP-LISTEN:8081' | grep -v grep | awk '{print $2}'"
        pid: str = subprocess.getoutput(cmd)
        
        self.logger.info(f"Current socat PID: {pid}")

        if pid:
            self.logger.info(f"Killing socat process: {pid}")
            kill_result = subprocess.getoutput(f"kill -9 {pid}")
            self.logger.info(f"Kill result: {kill_result}")

        time.sleep(5)
        
        socat_cmd = f"nohup socat -t0 TCP-LISTEN:{self.socat_port},fork,reuseaddr TCP:localhost:{self.next_port} &>/dev/null &"
        self.logger.info(f"Starting new socat process: {socat_cmd}")
        os.system(socat_cmd)
        
        # Verify socat is running and forwarding to correct port
        time.sleep(2)
        verify_cmd = "ps aux | grep 'socat -t0 TCP-LISTEN:8081' | grep -v grep"
        verify_result = subprocess.getoutput(verify_cmd)
        self.logger.info(f"New socat process: {verify_result}")
        
        if f"localhost:{self.next_port}" in verify_result:
            self.logger.info(f"Socat successfully forwarding to port {self.next_port}")
        else:
            self.logger.error(f"Socat not forwarding to correct port! Output: {verify_result}")

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
        
        # 현재 및 이전 컨테이너 상태 확인
        cmd = "docker ps -a | grep gdgoc-bugburger"
        status_before = subprocess.getoutput(cmd)
        self.logger.info(f"Containers before deployment:\n{status_before}")
        
        self._find_current_service()
        self.logger.info(f"Current service: {self.current_name} on port {self.current_port}")

        self._find_next_service()
        self.logger.info(f"Next service: {self.next_name} on port {self.next_port}")

        # 항상 next_name 컨테이너가 있으면 제거
        self._remove_container(self.next_name)
        self.logger.info(f"Removed container for next deployment: {self.next_name}")

        # 새 컨테이너 시작
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

        # Port forwarding 전환
        self._switch_port()
        self.logger.info("Switched port forwarding successfully")

        # 이전 컨테이너 정리 - 명시적으로 old_container 변수 저장
        old_container = self.current_name
        self.logger.info(f"Old container to be removed: {old_container}")
        
        # Wait a moment to ensure traffic is now going to new container
        time.sleep(5)
        
        # 이전 컨테이너 제거
        self._remove_container(old_container)
        self.logger.info(f"Removed old container: {old_container}")
        
        # 배포 후 최종 상태 확인
        cmd = "docker ps -a | grep gdgoc-bugburger"
        status_after = subprocess.getoutput(cmd)
        self.logger.info(f"Containers after deployment:\n{status_after}")

        print("Switched service successfully!")
        self.logger.info("Service update completed successfully!")


if __name__ == "__main__":
    manager = ServiceManager()
    manager.update_service()