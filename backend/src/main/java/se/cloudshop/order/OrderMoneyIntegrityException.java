package se.cloudshop.order;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public final class OrderMoneyIntegrityException extends RuntimeException {
  public OrderMoneyIntegrityException() {
    super("Stored invoice money is inconsistent; investigate the original data before continuing.");
  }
}
