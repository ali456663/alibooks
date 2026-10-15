package se.cloudshop.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public final class ProductMoneyIntegrityException extends RuntimeException {
  public ProductMoneyIntegrityException() {
    super("Stored service price is inconsistent; investigate the original data before continuing.");
  }
}
