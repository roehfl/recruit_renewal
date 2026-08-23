package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.MenuSite;
import com.shinyoung.recruit.enumeration.MenuType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
public class Menu extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private MenuSite site;
    @Enumerated(EnumType.STRING)
    private MenuType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Menu parent;

    private String name;
    private String path;

    private Integer sortOrder;

    @Column(length = 100)
    private String icon;

    private Menu(
            MenuSite site,
            MenuType type,
            Menu parent,
            String name,
            String path,
            Integer sortOrder,
            String icon
    ) {
        this.site = site;
        this.type = type;
        this.parent = parent;
        this.name = name;
        this.path = path;
        this.sortOrder = sortOrder;
        this.icon = icon;
    }

    public static Menu create(
            MenuSite site,
            MenuType type,
            Menu parent,
            String name,
            String path,
            Integer sortOrder,
            String icon
    ) {
        return new Menu(site, type, parent, name, path, sortOrder, icon);
    }

    public void update(
            MenuSite site,
            MenuType type,
            Menu parent,
            String name,
            String path,
            Integer sortOrder,
            String icon
    ) {
        this.site = site;
        this.type = type;
        this.parent = parent;
        this.name = name;
        this.path = path;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.icon = icon;
    }

    public boolean isRootMenu() {
        return this.parent == null;
    }

    public boolean isSubMenu() {
        return this.parent != null;
    }
}
