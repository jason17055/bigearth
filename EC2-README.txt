Launch instance
Authorize port 2626
  ec2-authorize quicklaunch-1 -p 2626

SSH to the instance and run these commands
  wget http://jason.long.name/trains/launch-server.sh
  sh launch-server.sh
