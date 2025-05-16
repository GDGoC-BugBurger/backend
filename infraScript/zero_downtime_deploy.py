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
                logging.StreamHandler(sys.stdout) # Ensure logs go to stdout for SSM/Actions
            ]
        )
        self.logger = logging.getLogger(__name__)
        self.services = {
            'gdgoc-bugburger_1': 8082,
            'gdgoc-bugburger_2': 8083
        }
        self.socat_port = 8081

    def execute_command(self, command, check_errors=True):
        """Execute command with proper error handling"""
        self.logger.info(f"Executing: {command}")
        try:
            result = subprocess.run(command, shell=True, check=check_errors, 
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   text=True)
            if result.stdout:
                self.logger.info(f"Output: {result.stdout.strip()}")
            if result.stderr: # Log stderr even on success, as some tools output info there
                self.logger.info(f"Stderr: {result.stderr.strip()}")
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            self.logger.error(f"Command failed: {command}")
            if e.stdout:
                self.logger.error(f"Stdout: {e.stdout.strip()}")
            if e.stderr:
                self.logger.error(f"Stderr: {e.stderr.strip()}")
            return None
        except Exception as e:
            self.logger.error(f"An unexpected error occurred while executing command: {command}. Error: {str(e)}")
            return None

    def find_active_container(self):
        """Find which container is currently active via socat, then Docker, then default."""
        self.logger.info("Attempting to find active container...")
        
        # Check socat forwarding first
        # Allow grep to not find anything without erroring out immediately in execute_command
        socat_check_cmd = "ps aux | grep 'socat -t0 TCP-LISTEN:8081' | grep -v grep || true"
        socat_ps_output = self.execute_command(socat_check_cmd, check_errors=False) # Allow no match
        
        if socat_ps_output:
            self.logger.info(f"Socat process found: {socat_ps_output}")
            if f"TCP:localhost:{self.services['gdgoc-bugburger_1']}" in socat_ps_output:
                self.logger.info("Socat points to gdgoc-bugburger_1 (port 8082).")
                return 'gdgoc-bugburger_1', self.services['gdgoc-bugburger_1']
            if f"TCP:localhost:{self.services['gdgoc-bugburger_2']}" in socat_ps_output:
                self.logger.info("Socat points to gdgoc-bugburger_2 (port 8083).")
                return 'gdgoc-bugburger_2', self.services['gdgoc-bugburger_2']
            self.logger.info("Socat process found but not pointing to a known service port.")
        else:
            self.logger.info("No active socat process found for TCP-LISTEN:8081.")

        # Fallback: Check running Docker containers
        self.logger.info("Falling back to check running Docker containers for active service.")
        # Check for _1 running
        c1_running_cmd = f"sudo docker ps --format '{{.Names}}' --filter name=^gdgoc-bugburger_1$ --filter status=running"
        if self.execute_command(c1_running_cmd):
            self.logger.info("gdgoc-bugburger_1 is running. Considering it active.")
            return 'gdgoc-bugburger_1', self.services['gdgoc-bugburger_1']
        
        # Check for _2 running
        c2_running_cmd = f"sudo docker ps --format '{{.Names}}' --filter name=^gdgoc-bugburger_2$ --filter status=running"
        if self.execute_command(c2_running_cmd):
            self.logger.info("gdgoc-bugburger_2 is running. Considering it active.")
            return 'gdgoc-bugburger_2', self.services['gdgoc-bugburger_2']

        self.logger.info("No specific active container found. Defaulting to gdgoc-bugburger_1 as target for new deployment.")
        return 'gdgoc-bugburger_1', self.services['gdgoc-bugburger_1'] # Default if none clearly active

    def remove_container(self, name):
        """Force remove a container. Returns True if removed or not found, False otherwise."""
        self.logger.info(f"Attempting to remove container: {name}")
        
        # Precise check for container existence (running or stopped)
        check_exists_cmd = f"sudo docker ps -a --format '{{.Names}}' --filter name=^{name}$"
        container_output = self.execute_command(check_exists_cmd)

        if not container_output or name not in container_output:
            self.logger.info(f"Container {name} does not exist or already removed.")
            return True

        self.logger.info(f"Container {name} found. Proceeding with stop and remove.")
        
        self.execute_command(f"sudo docker stop --time=10 {name}", check_errors=False) # Allow error if already stopped
        time.sleep(3) 
        
        self.execute_command(f"sudo docker rm -f {name}", check_errors=False) # Force remove
        time.sleep(3)
        
        # Verification
        final_check_output = self.execute_command(check_exists_cmd)
        if not final_check_output or name not in final_check_output:
            self.logger.info(f"Successfully removed container {name}.")
            return True
        else:
            self.logger.warning(f"Failed to remove container {name} with stop/rm. Attempting kill.")
            self.execute_command(f"sudo docker kill {name}", check_errors=False)
            time.sleep(2)
            self.execute_command(f"sudo docker rm -f {name}", check_errors=False)
            time.sleep(2)
            
            last_check_output = self.execute_command(check_exists_cmd)
            if not last_check_output or name not in last_check_output:
                self.logger.info(f"Successfully removed container {name} after kill.")
                return True
            else:
                self.logger.error(f"CRITICAL: Failed to remove container {name} even after kill and multiple rm -f attempts.")
                return False

    def start_container(self, name, port):
        """Start a new container. Returns True on success, False otherwise."""
        self.logger.info(f"Starting container {name} on port {port}")
        # Ensure old one with same name is gone first
        if not self.remove_container(name):
             self.logger.warning(f"Pre-start removal of {name} failed. 'docker run' might conflict if it still exists.")
        
        cmd = (f"sudo docker run -d --name={name} --restart unless-stopped "
               f"-p {port}:8080 -e TZ=Asia/Seoul "
               f"-v /dockerProjects/gdgoc-bugburger/volumes/gen:/gen "
               f"--pull always ghcr.io/whqtker/gdgoc-bugburger")
        
        start_output = self.execute_command(cmd)
        if start_output is None: # Command execution failed
            self.logger.error(f"Failed to execute docker run command for {name}.")
            return False
        
        # Verify container is running
        time.sleep(5) # Give it a moment to appear in `docker ps`
        check_running_cmd = f"sudo docker ps --format '{{.Names}}' --filter name=^{name}$ --filter status=running"
        running_output = self.execute_command(check_running_cmd)
        if running_output and name in running_output:
            self.logger.info(f"Container {name} started successfully (Docker ID: {start_output}).")
            return True
        else:
            self.logger.error(f"Container {name} failed to start or is not in running state after 'docker run'.")
            logs_cmd = f"sudo docker logs {name}" # Attempt to get logs if it exists
            self.execute_command(logs_cmd, check_errors=False)
            return False

    def switch_socat(self, target_port):
        """Switch socat to the new port. Returns True on success, False on failure."""
        self.logger.info(f"Attempting to switch socat to target port {target_port}")

        # Kill existing socat processes listening on self.socat_port
        # Using pkill for more robustness if awk parsing is tricky
        kill_cmd = f"sudo pkill -f 'socat -t0 TCP-LISTEN:{self.socat_port},fork,reuseaddr'"
        self.logger.info(f"Killing existing socat processes: {kill_cmd}")
        self.execute_command(kill_cmd, check_errors=False) # Don't fail if no process found
        time.sleep(3) # Wait for PIDs to be released

        # Start new socat
        socat_start_cmd = (f"sudo nohup socat -t0 "
                           f"TCP-LISTEN:{self.socat_port},fork,reuseaddr "
                           f"TCP:localhost:{target_port} &>/dev/null &")
        self.logger.info(f"Starting new socat: {socat_start_cmd}")
        
        # Execute with subprocess.run, shell=True for nohup and &
        start_result = self.execute_command(socat_start_cmd, check_errors=True)
        if start_result is None and not "nohup: ignoring input" in str(start_result): # nohup might give a non-error message to stderr
            # A bit hard to check success of nohup detached command directly from subprocess.run
            # We rely on the verification step.
            pass # Assuming it launched if no direct error.

        time.sleep(3) # Give socat time to start

        # Verify new socat process is running and pointing to the correct port
        verification_attempts = 3
        for attempt in range(verification_attempts):
            self.logger.info(f"Verifying socat status (attempt {attempt + 1}/{verification_attempts})...")
            # Allow grep to not find anything without erroring out
            socat_ps_check_cmd = f"ps aux | grep 'socat -t0 TCP-LISTEN:{self.socat_port}' | grep 'TCP:localhost:{target_port}' | grep -v grep || true"
            ps_output = self.execute_command(socat_ps_check_cmd, check_errors=False)
            
            if ps_output and f"TCP:localhost:{target_port}" in ps_output:
                self.logger.info(f"Socat successfully switched and verified for port {target_port}.")
                return True
            
            self.logger.warning(f"Socat verification attempt {attempt + 1}/{verification_attempts} failed or socat not pointing to {target_port}.")
            if attempt < verification_attempts - 1:
                time.sleep(3)
        
        self.logger.error(f"Failed to verify socat switch to port {target_port} after {verification_attempts} attempts.")
        return False

    def is_service_up(self, port):
        """Check if service is healthy"""
        url = f"http://127.0.0.1:{port}/actuator/health"
        try:
            self.logger.info(f"Checking health at {url}")
            response = requests.get(url, timeout=10) # Increased timeout
            if response.status_code == 200:
                health_status = response.json().get('status')
                if health_status == 'UP':
                    self.logger.info(f"Health check PASSED for port {port}: Status UP")
                    return True
                else:
                    self.logger.warning(f"Health check for port {port} returned status {health_status}.")
                    return False
            else:
                self.logger.warning(f"Health check for port {port} failed with status code {response.status_code}.")
                return False
        except requests.exceptions.RequestException as e:
            self.logger.warning(f"Health check failed for port {port}: {str(e)}")
            return False

    def update_service(self):
        """Main update service method"""
        self.logger.info("=== Starting Zero-Downtime Deployment Process ===")
        
        # STEP 1: Initial cleanup if both containers are somehow present (optional safeguard)
        c1_exists_cmd = f"sudo docker ps -a --format '{{.Names}}' --filter name=^gdgoc-bugburger_1$"
        c2_exists_cmd = f"sudo docker ps -a --format '{{.Names}}' --filter name=^gdgoc-bugburger_2$"
        c1_is_present = self.execute_command(c1_exists_cmd) is not None
        c2_is_present = self.execute_command(c2_exists_cmd) is not None

        if c1_is_present and c2_is_present:
            self.logger.warning("Both gdgoc-bugburger_1 and gdgoc-bugburger_2 found. This is unusual. Attempting to remove both for a clean start.")
            self.remove_container('gdgoc-bugburger_1') # Result not strictly checked here, best effort
            self.remove_container('gdgoc-bugburger_2')
            time.sleep(3)
        
        # STEP 2: Determine current and next container configurations
        current_name, current_port = self.find_active_container()
        self.logger.info(f"Determined current/old service (or default): {current_name} (port {current_port})")
        
        next_name = 'gdgoc-bugburger_2' if current_name == 'gdgoc-bugburger_1' else 'gdgoc-bugburger_1'
        next_port = self.services[next_name]
        self.logger.info(f"Target new service: {next_name} (port {next_port})")
        
        # STEP 3: Ensure the slot for the next container is clear
        self.logger.info(f"Ensuring target slot {next_name} is clear before starting.")
        if not self.remove_container(next_name): # This removes if it exists
            self.logger.warning(f"Could not confirm removal of existing {next_name}. 'docker run' might fail if name conflicts.")
            # Not exiting here, as start_container will fail if name is taken.
        
        # STEP 4: Start the next (new) container
        if not self.start_container(next_name, next_port):
             self.logger.error(f"FATAL: Failed to start new container {next_name}. Deployment aborted.")
             sys.exit(1)

        # STEP 5: Wait for the new service to be healthy
        self.logger.info(f"Waiting for {next_name} on port {next_port} to become healthy...")
        max_health_attempts = 24 # e.g. 24 * 5s = 2 minutes
        healthy = False
        for i in range(max_health_attempts):
            if self.is_service_up(next_port):
                self.logger.info(f"Service {next_name} is UP and HEALTHY on port {next_port}.")
                healthy = True
                break
            self.logger.info(f"Waiting for {next_name} health... (Attempt {i+1}/{max_health_attempts})")
            time.sleep(5) 
        
        if not healthy:
            self.logger.error(f"FATAL: Service {next_name} failed to become healthy after {max_health_attempts} attempts. Deployment aborted.")
            self.logger.info(f"Attempting to clean up the failed new container {next_name}.")
            self.remove_container(next_name)
            sys.exit(1)
        
        # STEP 6: Switch socat to the new port
        self.logger.info(f"Switching socat to new service {next_name} on port {next_port}.")
        if not self.switch_socat(next_port):
            self.logger.error(f"FATAL: Failed to switch socat to port {next_port}. Deployment aborted.")
            self.logger.info(f"Attempting to clean up the new container {next_name} as socat switch failed.")
            self.remove_container(next_name)
            sys.exit(1)
        
        self.logger.info(f"Successfully switched socat to {next_name} on port {next_port}.")
        
        # STEP 7: Wait for traffic to stabilize
        self.logger.info("Waiting briefly for traffic to stabilize on the new container...")
        time.sleep(15) # Increased wait time
        
        # STEP 8: Remove the old container (current_name)
        # Check if current_name is different from next_name (it should be)
        # and if it actually needs removal (it might have been the default and never existed, or removed in STEP 1)
        if current_name != next_name:
            self.logger.info(f"Attempting to remove old container: {current_name}")
            if not self.remove_container(current_name):
                # This is the critical failure point the user might be experiencing.
                self.logger.error(f"CRITICAL ERROR: Failed to remove old container {current_name}. Manual intervention may be required.")
                # Decide if script should exit with error here. For now, it will continue to final verification.
            else:
                self.logger.info(f"Old container {current_name} removed successfully.")
        else:
            self.logger.info(f"Old container name {current_name} is same as new; skipping its removal (should not happen in blue/green).")


        # STEP 9: Final verification
        self.logger.info("=== Performing Final Verification ===")
        
        final_next_running_cmd = f"sudo docker ps --format '{{.Names}}' --filter name=^{next_name}$ --filter status=running"
        final_current_exists_cmd = f"sudo docker ps -a --format '{{.Names}}' --filter name=^{current_name}$"

        is_next_running = self.execute_command(final_next_running_cmd) is not None
        current_still_exists = self.execute_command(final_current_exists_cmd) is not None if current_name != next_name else False


        if is_next_running and not current_still_exists:
            self.logger.info(f"SUCCESS: Deployment completed! New container {next_name} is running. Old container {current_name} is removed.")
        else:
            self.logger.error("DEPLOYMENT INCOMPLETE OR FAILED. Final state:")
            if not is_next_running:
                self.logger.error(f"  - New container {next_name} is NOT running.")
            else:
                self.logger.info(f"  - New container {next_name} IS running.")
            
            if current_still_exists:
                self.logger.error(f"  - Old container {current_name} IS STILL PRESENT.")
            else:
                 self.logger.info(f"  - Old container {current_name} is confirmed removed (or was never an issue).")
            
            self.logger.error("Please check Docker status manually: `sudo docker ps -a | grep gdgoc-bugburger`")
            sys.exit(1) # Exit with error if final state is not perfect
            
        self.logger.info("=== Zero-Downtime Deployment Script Finished Successfully ===")

if __name__ == "__main__":
    manager = ServiceManager()
    try:
        manager.update_service()
    except SystemExit as e: # Catch sys.exit to log exit code
        manager.logger.info(f"Script exiting with code {e.code}")
        sys.exit(e.code)
    except Exception as e:
        manager.logger.error(f"Unhandled fatal error in deployment script: {str(e)}", exc_info=True)
        sys.exit(1)