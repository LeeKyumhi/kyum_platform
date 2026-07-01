package com.guidematch.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuideCredentialRepository extends JpaRepository<GuideCredential, Long> {

    List<GuideCredential> findByGuideProfileId(Long guideProfileId);
}
