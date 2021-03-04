package fourthcafe;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Inventory_table")
public class Inventory {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;

    @PrePersist
    public void onPrePersist(){
        Warehoused warehoused = new Warehoused();
        BeanUtils.copyProperties(this, warehoused);
        warehoused.publishAfterCommit();


        InventoryCanceled inventoryCanceled = new InventoryCanceled();
        BeanUtils.copyProperties(this, inventoryCanceled);
        inventoryCanceled.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }




}
