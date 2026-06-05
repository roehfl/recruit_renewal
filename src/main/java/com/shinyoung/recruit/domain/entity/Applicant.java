package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.common.crypto.AesAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@PrimaryKeyJoinColumn(name = "user_id")
public class Applicant extends User {
    @Column(unique = true)
    private String email;
    private String userName;
    private String password;
    private String phoneNumber;
    @Convert(converter = AesAttributeConverter.class)
    private String ci;
    @Column(nullable = false, unique = true)
    private String ciHash;

    protected Applicant() {}

    public Applicant(String ci, String ciHash) {
        this.ci = ci;
        this.ciHash = ciHash;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changePhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
