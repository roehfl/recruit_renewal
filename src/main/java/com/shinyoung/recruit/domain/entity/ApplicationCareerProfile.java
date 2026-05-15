package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.CareerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "application_career_profile",
        indexes = {
                @Index(name = "idx_application_career_profile_application", columnList = "job_application_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationCareerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false, unique = true)
    private JobApplication jobApplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareerType careerType;

    private ApplicationCareerProfile(JobApplication jobApplication, CareerType careerType) {
        this.jobApplication = jobApplication;
        this.careerType = careerType;
    }

    public static ApplicationCareerProfile create(JobApplication jobApplication, CareerType careerType) {
        return new ApplicationCareerProfile(jobApplication, careerType);
    }

    public void updateCareerType(CareerType careerType) {
        this.careerType = careerType;
    }
}
