package com.vori.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// 서비스의 "오늘"(LocalDate.now())·자정 정산(Asia/Seoul cron)·DB 연결(serverTimezone=Asia/Seoul)이 같은 날짜를 보게
		// 서버 시간대를 한국 시간으로 고정한다. UTC 서버에서 띄우면 00~09시 사이에 판정 날짜가 하루 어긋났다.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(BackendApplication.class, args);
	}

}
