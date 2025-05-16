#!/usr/bin/env python3

import os
import requests
import subprocess
import time
import logging
import sys

class ServiceManager:
    def __init__(self):
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler('/dockerProjects/gdgoc-bugburger/deploy.log'),
                logging.StreamHandler()
            ]
        )
        self.logger = logging.getLogger(__name__)
        self.services = {
            'gdgoc-bugburger_1': 8082,
            'gdgoc-bugburger_2': 8083
        }
        self.socat_port = 8081

    def execute_command(self, command):
        """Execute command with proper error handling"""
        self.logger.info(f"Executing: {command}")
        try:
            result = subprocess.run(command, shell=True, check=True, 
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   text=True)
            self.logger.info(f"Output: {result.stdout}")
            return result.stdout
        except subprocess.CalledProcessError as e:
            self.logger.error(f"Command failed: {e.stderr}")
            return None

    def find_active_container(self):
        """Find which container is currently active"""
        # Check running containers
        running = self.execute_command("sudo docker ps --format '{{.Names}}' | grep gdgoc-bugburger")
        self.logger.info(f"Running containers: {running}")
        
        # Check socat forwarding
        socat = self.execute_command("ps aux | grep 'socat -t0 TCP-LISTEN:8081' | grep -v grep")
        self.logger.info(f"Socat process: {socat}")
        
        # Determine active container
        if socat and "localhost:8082" in socat:
            return 'gdgoc-bugburger_1', 8082
        elif socat and "localhost:8083" in socat:
            return 'gdgoc-bugburger_2', 8083
        elif running and 'gdgoc-bugburger_1' in running:
            return 'gdgoc-bugburger_1', 8082
        elif running and 'gdgoc-bugburger_2' in running:
            return 'gdgoc-bugburger_2', 8083
        else:
            return 'gdgoc-bugburger_1', 8082  # Default to first container

    def remove_container(self, name):
        """Force remove a container with sudo"""
        self.logger.info(f"Removing container: {name}")
        
        # First attempt: stop the container gracefully
        self.execute_command(f"sudo docker stop --time=5 {name}")
        time.sleep(2)
        
        # Second attempt: force remove 
        self.execute_command(f"sudo docker rm -f {name}")
        time.sleep(2)
        
        # Verify container was removed
        result = self.execute_command(f"sudo docker ps -a --format '{{.Names}}' | grep -w {name}")
        if result and name in result:
            self.logger.error(f"Failed to remove container {name}, trying kill command")
            self.execute_command(f"sudo docker kill {name}")
            self.execute_command(f"sudo docker rm -f {name}")
        
        # Final verification
        result = self.execute_command(f"sudo docker ps -a --format '{{.Names}}' | grep -w {name}")
        return not (result and name in result)

    def start_container(self, name, port):
        """Start a new container with sudo"""
        self.logger.info(f"Starting container {name} on port {port}")
        cmd = f"sudo docker run -d --name={name} --restart unless-stopped -p {port}:8080 -e TZ=Asia/Seoul -v /dockerProjects/gdgoc-bugburger/volumes/gen:/gen --pull always ghcr.io/whqtker/gdgoc-bugburger"
        return self.execute_command(cmd)

    def switch_socat(self, port):
        """Switch socat to the new port"""
        self.logger.info(f"Switching socat to port {port}")
        
        # Kill existing socat
        pid = self.execute_command("ps aux | grep 'socat -t0 TCP-LISTEN:8081' | grep -v grep | awk '{print $2}'")
        if pid:
            self.execute_command(f"sudo kill -9 {pid}")
        time.sleep(3)
        
        # Start new socat
        cmd = f"sudo nohup socat -t0 TCP-LISTEN:{self.socat_port},fork,reuseaddr TCP:localhost:{port} &>/dev/null &"
        os.system(cmd)
        time.sleep(2)
        
        # Verify
        result = self.execute_command("ps aux | grep 'socat -t0 TCP-LISTEN:8081' | grep -v grep")
        return result and f"localhost:{port}" in result

    def is_service_up(self, port):
        """Check if service is healthy"""
        url = f"http://127.0.0.1:{port}/actuator/health"
        try:
            self.logger.info(f"Checking health at {url}")
            response = requests.get(url, timeout=5)
            return response.status_code == 200 and response.json().get('status') == 'UP'
        except Exception as e:
            self.logger.info(f"Health check failed: {str(e)}")
            return False

    def update_service(self):
        """Main update service method"""
        self.logger.info("Starting deployment process")
        
        # STEP 1: Check for running containers, remove all if both are running
        running = self.execute_command("sudo docker ps -a | grep gdgoc-bugburger")
        if running and 'gdgoc-bugburger_1' in running and 'gdgoc-bugburger_2' in running:
            self.logger.info("Both containers running - removing both for clean start")
            self.remove_container('gdgoc-bugburger_1')
            self.remove_container('gdgoc-bugburger_2')
            time.sleep(3)
        
        # STEP 2: Find current active container or choose a default
        current_name, current_port = self.find_active_container()
        self.logger.info(f"Current service: {current_name} on port {current_port}")
        
        # STEP 3: Determine next container
        next_name = 'gdgoc-bugburger_2' if current_name == 'gdgoc-bugburger_1' else 'gdgoc-bugburger_1'
        next_port = 8083 if next_name == 'gdgoc-bugburger_2' else 8082
        self.logger.info(f"Next service: {next_name} on port {next_port}")
        
        # STEP 4: Remove the next container if it exists
        self.remove_container(next_name)
        
        # STEP 5: Start the next container
        self.start_container(next_name, next_port)
        
        # STEP 6: Wait for service to be healthy
        max_attempts = 20
        for i in range(max_attempts):
            if self.is_service_up(next_port):
                self.logger.info(f"Service is up on port {next_port}")
                break
            if i == max_attempts - 1:
                self.logger.error("Service failed to start. Aborting.")
                return
            self.logger.info(f"Waiting for service... ({i+1}/{max_attempts})")
            time.sleep(3)
        
        # STEP 7: Switch socat to the new port
        success = self.switch_socat(next_port)
        if not success:
            self.logger.error("Failed to switch socat")
            return
        
        # STEP 8: Wait to ensure traffic is flowing to new container
        time.sleep(10)
        
        # STEP 9: Remove the old container
        self.remove_container(current_name)
        
        # STEP 10: Final verification
        final_check = self.execute_command("sudo docker ps | grep gdgoc-bugburger")
        if next_name not in final_check:
            self.logger.error(f"ERROR: New container {next_name} not found in final check!")
        else:
            self.logger.info("Deployment completed successfully!")
        
        # Check for unexpected containers
        if current_name in final_check:
            self.logger.error(f"ERROR: Old container {current_name} still running!")
            self.execute_command(f"sudo docker rm -f {current_name}")


if __name__ == "__main__":
    try:
        ServiceManager().update_service()
    except Exception as e:
        print(f"Fatal error: {str(e)}")
        logging.error(f"Fatal error: {str(e)}", exc_info=True)
        sys.exit(1)