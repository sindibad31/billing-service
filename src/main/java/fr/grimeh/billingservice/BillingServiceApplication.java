package fr.grimeh.billingservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.config.Projection;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.*;
import java.util.Collection;
import java.util.Date;

//@EnableEurekaClient

@SpringBootApplication
@EnableFeignClients
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
    @Bean
    CommandLineRunner start(BillRepository billRepository, ProductItemRepository productItemRepository, CustomerService customerService, InventoryService inventoryService) {
        return args -> {
            Customer c1 = customerService.findCustomerById(1L);
            System.out.println("***********************");
            System.out.println("ID = " + c1.getId());
            System.out.println("NAME = " + c1.getName());
            System.out.println("EMAIL = " + c1.getEmail());
            System.out.printf("************************");
            Bill bill1 = billRepository.save(new Bill(null, new Date(), c1.getId(), null, null));

            PagedModel<Product> products = inventoryService.findAllProducts();
            products.getContent().forEach(product -> {
                productItemRepository.save(new ProductItem(null, product.getId(), null, product.getPrice(), 30, bill1));
            });
        };
    }
}

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @ToString
class Bill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Date billingDate;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long customerId;
    @Transient
    private Customer customer;
    @OneToMany(mappedBy = "bill")
    private Collection<ProductItem> productItems;
}

@RepositoryRestResource
interface BillRepository extends JpaRepository<Bill, Long>{}

@Projection(name = "fullBill", types = Bill.class)
interface BillProjection {
    public Long getId();
    public Date getBillingDate();
    public Long getCustomerId();
    public Collection<ProductItem> getProductItems();
}

@RestController
class BillRestController {
    @Autowired
    private BillRepository billRepository;
    @Autowired
    private ProductItemRepository productItemRepository;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/fullBill/{id}")
    public Bill getBill(@PathVariable(name = "id") Long id) {
        Bill bill = billRepository.findById(id).get();
        bill.setCustomer(customerService.findCustomerById(bill.getCustomerId()));
        bill.getProductItems().forEach(pi -> {
            pi.setProduct(inventoryService.findProductById(pi.getProductId()));
        });
        return bill;
    }
}





@Entity
@Data @NoArgsConstructor @AllArgsConstructor @ToString
class ProductItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long productId;
    @Transient
    private Product product;
    private double price;
    private double quantity;
    @ManyToOne
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Bill bill;
}

@RepositoryRestResource
interface ProductItemRepository extends JpaRepository<ProductItem, Long>{}

@Data
class Customer {
    private Long id;
    private String name;
    private String email;
}

@FeignClient(name = "CUSTOMER-SERVICE")
interface CustomerService {
    @GetMapping("/customers/{id}")
    public Customer findCustomerById(@PathVariable(name = "id") Long id);
}

@Data
class Product {
    private Long id;
    private String name;
    private double price;
}

@FeignClient(name = "INVENTORY-SERVICE")
interface InventoryService {
    @GetMapping("/products/{id}")
    public Product findProductById(@PathVariable(name = "id") Long id);
    @GetMapping("/products")
    public PagedModel<Product> findAllProducts();
}

