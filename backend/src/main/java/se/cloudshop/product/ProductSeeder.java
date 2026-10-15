package se.cloudshop.product;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.demo-products", name = "seed-enabled", havingValue = "true")
public class ProductSeeder implements CommandLineRunner {

  private final ProductRepository productRepository;

  public ProductSeeder(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Override
  public void run(String... args) {
    if (productRepository.count() != 0) {
      return;
    }

    upsert("Sommarkampanj 3 manader", "Personligt anpassat traningsprogram, kostschema, uppfoljning och coachning under 3 manader. Galler till 31/8.", 1856);
    upsert("Tekniktraning 60 minuter", "Tekniktraningssession for korrekt teknik i specifika ovningar.", 499);
    upsert("Tekniktraning 60 minuter - ungdom/student/pensionar", "Rabatterad tekniktraningssession for ungdom, student eller pensionar.", 340);
    upsert("Individuell PT-traning 60 min", "Skraddarsydd personlig traning 60 minuter anpassad efter kundens mal.", 499);
    upsert("Individuell PT-traning 20 pass", "Paket med 20 individuella PT-pass inklusive traningsprogram, kostschema och uppfoljning.", 7850);
    upsert("PT online Komplett 26 veckor", "Komplett online coaching under 26 veckor med traning, kostschema, uppfoljning och ovningsvideo.", 4699);
    upsert("PT online Komplett 26 veckor - ungdom/student/pensionar", "Rabatterat komplett online coachingpaket under 26 veckor.", 4250);
    upsert("PT online Next Level 26", "Flersprakigt online coachingpaket under 26 veckor med traningsprogram, kostschema och obegransad support.", 4105);
    upsert("PT online Next Level 26 - ungdom/student/pensionar", "Rabatterat Next Level 26-paket for ungdom, student eller pensionar.", 3828);
    upsert("PT online Body Reboot 26", "Online coaching under 26 veckor for medel eller proffs.", 3850);
    upsert("PT online Body Reboot 26 - ungdom/student/pensionar", "Rabatterat Body Reboot 26-paket for ungdom, student eller pensionar.", 3250);
    upsert("PT online Livsstilsstarten", "Online coaching med traningsprogram, kostschema, uppfoljning, ovningsvideo och feedback.", 3105);
    upsert("PT online Livsstilsstarten - ungdom/student/pensionar", "Rabatterat Livsstilsstarten-paket for ungdom, student eller pensionar.", 2840);
    upsert("PT online Glute & Leg Specialisten 16 veckor", "16 veckors online coaching med fokus pa rumpa och ben, inklusive traning och kost.", 3000);
    upsert("PT online Glute & Leg Specialisten - ungdom/student/pensionar", "Rabatterat Glute & Leg Specialisten-paket for ungdom, student eller pensionar.", 2810);
    upsert("Avancerat Kostschema", "Komplett maltidsplan med 20 unika recept fordelade over 5 maltider per dag.", 2458);
    upsert("PT online Fokus 12", "12 veckors online coaching med traningsprogram, kostschema, uppfoljning och feedback.", 2469);
    upsert("PT online Fokus 12 - ungdom/student/pensionar", "Rabatterat Fokus 12-paket for ungdom, student eller pensionar.", 2215);
    upsert("PT online 8 veckors Styrka & Halsa", "8 veckors online coaching for styrka och halsa med komplett tranings- och kostupplagg.", 1829);
    upsert("PT online 8 veckors Styrka & Halsa - ungdom/student/pensionar", "Rabatterat 8 veckors Styrka & Halsa-paket for ungdom, student eller pensionar.", 1499);
    upsert("PT online Projekt 4 veckor", "Intensivt 4 veckors online coachingpaket for nystart, platabrott eller snabb struktur.", 1129);
    upsert("PT online Projekt 4 veckor - ungdom/student/pensionar", "Rabatterat Projekt 4 veckor-paket for ungdom, student eller pensionar.", 856);
    upsert("Black Friday 12 veckors transformation", "Black Friday-erbjudande for 12 veckors transformation.", 1919);
    upsert("PT online Julerbjudande 16 veckor", "Julerbjudande med 16 veckors online coaching.", 1999);
  }

  private void upsert(String name, String description, int price) {
    productRepository.save(new Product(name, description, price));
  }
}
