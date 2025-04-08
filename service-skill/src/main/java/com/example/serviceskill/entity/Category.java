package com.example.serviceskill.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Category {

    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @OneToMany(
            mappedBy = "category",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Skill> skills = new ArrayList<>();

    // Méthode helper pour la relation bidirectionnelle
    public void addSkill(Skill skill) {
        skills.add(skill);
        skill.setCategory(this);
    }

    public void removeSkill(Skill skill) {
        skills.remove(skill);
        skill.setCategory(null);
    }
}