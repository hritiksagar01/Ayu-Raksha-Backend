
echo "Stopping existing Spring Boot application..."
pkill -f 'java -jar' || echo "No existing process found."
