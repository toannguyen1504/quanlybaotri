package com.example.quanlybaotri.identity.application;

import com.example.quanlybaotri.identity.domain.*;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CustomerScopeMigrationRunner implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(CustomerScopeMigrationRunner.class);
    private final boolean enabled;
    private final UserRepository users;
    private final OrganizationClient organizations;
    private final TransactionTemplate transactions;

    public CustomerScopeMigrationRunner(@Value("${app.customer-scope-migration.enabled:false}") boolean enabled,
        UserRepository users,OrganizationClient organizations,TransactionTemplate transactions){
        this.enabled=enabled;this.users=users;this.organizations=organizations;this.transactions=transactions;
    }

    @Override public void run(ApplicationArguments args){
        if(!enabled)return;
        AtomicInteger migrated=new AtomicInteger(),skipped=new AtomicInteger(),failed=new AtomicInteger();
        for(var snapshot:users.findAll()){
            try{
                if(snapshot.getAccountType()==AccountType.INTERNAL){
                    transactions.executeWithoutResult(tx->{var user=users.findById(snapshot.getId()).orElseThrow();user.configureAccess(AccountType.INTERNAL,null,SiteScope.ALL_SITES,Set.of());});
                    migrated.incrementAndGet();continue;
                }
                if(snapshot.getCustomerId()!=null&&(snapshot.getSiteScope()==SiteScope.ALL_SITES||!snapshot.getSiteIds().isEmpty())){skipped.incrementAndGet();continue;}
                if(snapshot.getLegacySiteId()==null)throw new IllegalStateException("Customer account has no legacy site");
                var site=organizations.find(snapshot.getLegacySiteId());
                transactions.executeWithoutResult(tx->{var user=users.findById(snapshot.getId()).orElseThrow();user.configureAccess(AccountType.CUSTOMER,site.customerId(),SiteScope.SELECTED_SITES,Set.of(site.id()));});
                migrated.incrementAndGet();
            }catch(RuntimeException ex){failed.incrementAndGet();log.error("Customer scope migration failed userId={}",snapshot.getId(),ex);}
        }
        log.info("Customer scope migration completed migrated={} skipped={} failed={}",migrated,skipped,failed);
        if(failed.get()>0)throw new IllegalStateException("Customer scope migration has unresolved users: "+failed.get());
    }
}
