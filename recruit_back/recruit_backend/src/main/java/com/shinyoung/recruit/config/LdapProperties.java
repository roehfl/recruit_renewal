package com.shinyoung.recruit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * LDAP(AD) 접속 및 사용자/그룹 검색 설정.
 *
 * <p>접속정보와 검색 base/filter는 코드에 하드코딩하지 않고 외부 설정/환경변수로 주입한다(CLAUDE.md 3장).
 * 값은 성격에 따라 두 부류이며 취급 강도가 다르다.
 *
 * <ul>
 *   <li><b>자격증명</b>({@code managerDn}, {@code managerPassword}) — 저장소·로그 어디에도 남기지 않는다.
 *       유출 시 즉시 교체 대상이므로 기본값을 두지 않는다.</li>
 *   <li><b>환경/조직 정보</b>({@code url}, {@code base}, {@code userSearchBase}, {@code groupSearchBase}) —
 *       자격증명은 아니지만 내부망 주소와 회사 조직 구조가 드러나므로 실제 값을 저장소에 남기지 않는다.
 *       유출돼도 교체할 대상은 없다.</li>
 * </ul>
 *
 * <p>로컬 개발에서는 LDAP을 쓰지 않을 수 있으므로 {@code @NotBlank}로 강제하지 않는다.
 * 대신 설정이 비어 있으면 기동 시 경고를 남기고({@link AuthenticationConfig}), 실제 인증 시점에 실패한다.
 * {@code userSearchFilter}만 AD 표준 관용구라 기본값을 둔다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.ldap")
public class LdapProperties {

    /** 미설정 상태를 나타내는 URL. 이 값이면 실제 접속은 불가능하다. */
    static final String UNSET_URL = "ldap://";

    /** LDAP 서버 URL(예: {@code ldap://ad.example.com:389}). */
    private String url = UNSET_URL;

    /** 모든 DN의 기준이 되는 base DN. */
    private String base = "";

    /** 검색용 바인드 계정 DN. 자격증명이므로 기본값을 두지 않는다. */
    private String managerDn = "";

    /** 검색용 바인드 계정 비밀번호. 자격증명이므로 기본값을 두지 않는다. */
    private String managerPassword = "";

    /** 사용자 엔트리를 찾을 검색 base(예: {@code ou=users,ou=Example}). */
    private String userSearchBase = "";

    /** 사용자 검색 필터. AD 표준 관용구라 기본값을 둔다. */
    private String userSearchFilter = "(sAMAccountName={0})";

    /** 그룹(부서) 검색 base(예: {@code ou=groups,ou=Example}). */
    private String groupSearchBase = "";

    /**
     * LDAP 인증에 필요한 최소 값이 모두 채워졌는지 여부.
     *
     * <p>{@code base}와 {@code groupSearchBase}는 디렉터리 구성에 따라 비어 있을 수 있어 확인 대상에서 제외한다.
     */
    public boolean isConfigured() {
        return StringUtils.hasText(url)
                && !UNSET_URL.equals(url)
                && StringUtils.hasText(managerDn)
                && StringUtils.hasText(managerPassword)
                && StringUtils.hasText(userSearchBase);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getManagerDn() {
        return managerDn;
    }

    public void setManagerDn(String managerDn) {
        this.managerDn = managerDn;
    }

    public String getManagerPassword() {
        return managerPassword;
    }

    public void setManagerPassword(String managerPassword) {
        this.managerPassword = managerPassword;
    }

    public String getUserSearchBase() {
        return userSearchBase;
    }

    public void setUserSearchBase(String userSearchBase) {
        this.userSearchBase = userSearchBase;
    }

    public String getUserSearchFilter() {
        return userSearchFilter;
    }

    public void setUserSearchFilter(String userSearchFilter) {
        this.userSearchFilter = userSearchFilter;
    }

    public String getGroupSearchBase() {
        return groupSearchBase;
    }

    public void setGroupSearchBase(String groupSearchBase) {
        this.groupSearchBase = groupSearchBase;
    }
}
