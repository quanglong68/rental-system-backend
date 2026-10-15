# HƯỚNG DẪN CHẠY DỰ ÁN TỪ LÚC CLONE CODE
Cần cài: Git, Java 21, Maven 3.9+, Docker Desktop đang chạy. Không cần cài Postgres/Kafka — chạy trong Docker. Để trống port 8080, 8761.  
copy .env trong zalo vào thư mục root be
mvn clean verify
# chờ Reactor 5/5 SUCCESS
cd .\infra
docker compose --env-file ..\.env up -d postgres kafka kafka-init discovery-server auth-service api-gateway
# Chờ tới khi TẤT CẢ healthy (gateway lên chậm nhất, khoảng 1-3 phút):
# rental-postgres, rental-kafka, rental-discovery, rental-auth, rental-gateway
