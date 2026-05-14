# Database

> VORI 의 MySQL 사용법·셋업 가이드. 스키마 명세는 별도 노트(아래 [명세 위치](#명세-위치)).

## 스택

| 항목 | 값 |
|---|---|
| DBMS | MySQL 8.x |
| ORM | Spring Data JPA (Hibernate) |
| 마이그레이션 | Flyway (예정) |
| DB 이름 | `vori` |
| 시드 데이터 | Spring `CommandLineRunner` 로 Java 에서 작성 |

---

## 로컬 셋업

### 1. MySQL 설치·기동 (macOS)

```bash
brew install mysql
brew services start mysql
```

### 2. 데이터베이스 생성

```bash
mysql -u root -e "CREATE DATABASE vori CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

- `utf8mb4` 필수 — 이모지·한국어 보존
- 사용자 분리는 졸작 스코프에선 `root` 그대로 사용해도 됨. 운영 단계엔 별도 계정

### 3. 접속 확인

```bash
mysql -u root -p
# 비밀번호 입력
mysql> USE vori;
mysql> SHOW TABLES;
```

처음엔 비어 있음. 스키마는 다음 단계에서 자동 생성.

---

## 스키마 적용

### 현재 (Flyway 도입 전)

`application.properties` 에 다음 두 줄이 있으면 Spring Boot 가 JPA Entity 클래스로부터 테이블을 자동 생성:

```properties
spring.jpa.hibernate.ddl-auto=update
```

- `update` — Entity 변경 시 알아서 컬럼 추가 (운영 단계 위험, 졸작 OK)
- `validate` — 스키마 검증만 (Flyway 도입 후 권장)

### Flyway 도입 후 (목표)

1. `build.gradle` 에 추가:
   ```gradle
   implementation 'org.flywaydb:flyway-core'
   implementation 'org.flywaydb:flyway-mysql'
   ```

2. 마이그레이션 파일 위치:
   ```
   backend/src/main/resources/db/migration/
     V1__init.sql                 # 초기 테이블 11종
     V2__add_<feature>.sql        # 이후 변경
   ```

3. `application.properties`:
   ```properties
   spring.jpa.hibernate.ddl-auto=validate
   spring.flyway.enabled=true
   ```

4. `./gradlew bootRun` 으로 앱 시작 시 Flyway 가 자동으로 미적용 SQL 만 실행 → 메타 테이블 `flyway_schema_history` 에 적용 이력 기록.

---

## `application.properties`

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/vori?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

비밀번호는 **`.env`** 또는 환경변수로:

```
# .env (커밋 금지 — .gitignore 에 포함됨)
DB_USERNAME=root
DB_PASSWORD=<실제값>
```

`.env.example` 에 키 이름만 두고 실제 값은 각자 `.env` 에. 평문 커밋 절대 금지.

---

## 시드 데이터

`categories` (44개) / `pet_species` / `theme_master` 등 마스터 데이터는 SQL INSERT 가 아니라 **Java 코드**로 작성:

```
backend/src/main/java/com/vori/backend/seeder/
  CategorySeeder.java         # 카테고리 44개
  PetSpeciesSeeder.java       # 펫 종류
  AdminPromoteRunner.java     # 본인 이메일을 ADMIN 으로 승격
```

각 시더는 `CommandLineRunner` 를 구현하고, 첫 줄에서 `if (repository.count() > 0) return;` 로 재실행 방지.

```java
@Component
@RequiredArgsConstructor
public class CategorySeeder implements CommandLineRunner {
    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) return;
        // ... 대분류 7 + 상세 37 시드
    }
}
```

이유:
- 도메인 객체의 검증 로직(`@Valid` 등) 거치므로 무결성 보장
- 환경별(dev / prod) 다른 시드 가능
- Flyway 와 분리 — 스키마는 SQL, 데이터는 Java

---

## 명세 위치

테이블 명세 (컬럼·타입·인덱스·FK·변경 이력) 는 Obsidian 볼트에 있음:

```
~/Documents/Obsidian Vault/Projects/vori/notes/db-table-spec.md
```

- 본문 = 현재 진실 (최신 컬럼 명세)
- 변경 이력 (#1 ~ #11) = 각 결정의 사유·날짜·DDL 스냅샷

코드 작성 전에 본문 + 관련 변경 이력 확인.

---

## 트러블슈팅

### `Access denied for user 'root'@'localhost'`
- `mysql -u root -p` 로 직접 접속해서 비밀번호 맞는지 확인
- 비번 모르면: `brew services stop mysql` → `mysqld_safe --skip-grant-tables &` → 비번 재설정

### 한글이 `???` 으로 저장됨
- DB 생성 시 `utf8mb4` 빠뜨림. 다시 생성하거나 `ALTER DATABASE vori CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`

### 앱 시작 시 `Table 'vori.users' doesn't exist`
- `spring.jpa.hibernate.ddl-auto=update` 인지 확인
- Entity 클래스에 `@Entity` + `@Table(name="users")` 붙어 있는지 확인

### Flyway 가 마이그레이션 충돌 (`checksum mismatch`)
- 이미 적용된 SQL 파일을 수정하면 발생. 새 `V<N+1>__fix.sql` 추가로 처리. 운영 단계에선 절대 기존 파일 수정 X

---

## 주의사항

- `.env` 는 절대 커밋 금지 (`.gitignore` 에 이미 포함)
- DB 비밀번호·API 키는 코드·로그·문서에 평문 X
- `ddl-auto=create-drop` 사용 금지 (앱 종료 시 테이블 다 날아감)
- 운영 단계 진입 시 `ddl-auto=validate` + Flyway 로 전환
- 마이그레이션 파일은 **append-only**. 이미 적용된 파일 수정 X, 새 파일 추가로만 변경
