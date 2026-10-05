package com.example.quanlybaotri.organization.api;

import com.example.quanlybaotri.organization.domain.Branch;
import com.example.quanlybaotri.organization.persistence.BranchRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/customers")
public class InternalCustomerController {
    private final BranchRepository customers;

    public InternalCustomerController(BranchRepository customers) {
        this.customers = customers;
    }

    @GetMapping("/{id}")
    public CustomerRef get(@PathVariable UUID id) {
        return CustomerRef.from(customers.findById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng")));
    }

    public record CustomerRef(UUID id, String code, String name, boolean active) {
        static CustomerRef from(Branch customer) {
            return new CustomerRef(customer.getId(), customer.getCode(), customer.getName(), customer.isActive());
        }
    }
}
