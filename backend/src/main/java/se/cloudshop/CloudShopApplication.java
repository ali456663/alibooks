package se.cloudshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CloudShopApplication {

  public static void main(String[] args) {
    SpringApplication.run(CloudShopApplication.class, args);
  }
}
