# BPMN Agent Backend (Spring Boot + JWT)

## Run

```bash
mvn spring-boot:run
```

## Test API with curl

### 1) Login as admin

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@bouygues.com","password":"admin123"}'
```

### 2) Login as user

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@bouygues.com","password":"user123"}'
```

### 3) Access protected endpoint

Replace `<TOKEN>` with the JWT returned by `/api/auth/login`.

```bash
curl -X GET http://localhost:8080/api/test \
  -H "Authorization: Bearer <TOKEN>"
```

### 4) Invalid credentials example

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@bouygues.com","password":"wrong"}'
```
