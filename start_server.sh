
echo "Starting Spring Boot application..."
cd /home/ubuntu/ayu-raksha-backend
nohup java -jar target/*.jar > app.log 2>&1 &
