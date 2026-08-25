<template>
  <section class="recruits-page">
    <div class="page-inner">

      <h1 class="page-title">개인정보처리방침</h1>
      <div class="policyList">
        <ol>
        <li class="policyListItem"
        v-for="item in PolicyList"
        :key="item.sort">
          <dl>
            <dt class="policyTitle">{{ item.title }}</dt>
            <html-view :content="item.description"/>
          </dl>
        </li>
      </ol>

      </div>
      
    </div>
  </section>   
</template>

<script setup lang="ts">
import { ref } from 'vue'
import HtmlView from '../common/htmlView.vue'

interface PrivacyPolicy {
  title: string
  description: string
  sort: number
}

const PolicyList = ref<PrivacyPolicy[]>([
  {
    title: '총칙',
    description: `
    <p>신영증권주식회사(이하 "당사"라고 합니다)는 「개인정보보호법」 및 「정보통신망 이용촉진 및 정보보호 등에 관한 법률」 등 관련 법령에 따라 귀하의 개인정보 및 권익을 보호하기 위하여 개인정보 처리방침을 두고, 이를 통해 귀하가 제공하는 개인정보가 어떠한 용도와 방식으로 이용되고 있으며 어떻게 보호되는지를 알려드립니다.</p>`,
    sort: 1
  },
  {
    title: '개인정보의 처리 목적',
    description: `
    <p>당사는 다음과 같은 목적을 위하여 개인정보를 처리하고 있습니다. 귀하가 제공한 모든 개인정보는 다음의 목적 이외로는 사용되지 않으며, 이용목적이 변경되거나 목적 이외로 활용할 경우에는 사전 동의를 구할 예정입니다.
    <ul>
    <li>본인식별, 채용전형 진행 및 공지사항 통지, 합격여부 확인, 자격요건 확인 등 채용을 위한 행정목적</li>
    </ul>`,
    sort: 2
  },
  {
    title: '개인정보의 처리 및 보유 기간',
    description: `
    <p>귀하의 개인정보는 회사의 인력풀에 저장되어 채용이 필요한 경우 해당 인력풀에서 상시채용을 위해 5년 동안 보관됩니다. 다만, 귀하가 개인정보의 삭제를 원하는 경우 지체없이 해당 정보를 삭제합니다.</p>`,
    sort: 3
  },
  {
    title: '개인정보의 제3자 제공',
    description: `
    <p>귀하의 개인정보는 수집 시 고지한 범위 내에서 이용되며, 귀하의 사전동의 없이는 동 범위를 초과하여 제3자에게 제공하거나 공유하지 않습니다.
      귀하의 정보를 제공하거나 공유하는 경우에는 사전에 제공받는 자, 제공목적, 제공되는 항목 등에 대해 개별적으로 서면 또는 유선 등의 방법으로 사전에 동의를 구합니다.
      당사는 현재 위 항의 목적으로 수집된 개인정보를 제3자에게 제공하고 있지 않습니다.
      <br><br>다만, 다음의 경우에는 사전동의 없이 개인정보를 제3자에게 제공할 수 있습니다.</p>
    <ul>
    <li>법률에 특별한 규정이 있거나 법령상 의무를 준수하기 위하여 불가피한 경우</li>
    <li>정보주체 또는 그 법정대리인이 의사표시를 할 수 없는 상태에 있거나 주소불명 등으로 사전 동의를 받을 수 없는 경우로서 명백히 정보주체 또는 제3자의 급박한 생명, 신체, 재산의 이익을 위하여 필요하다고 인정되는 경우</li>
    <li>통계작성 및 학술연구 등의 목적을 위하여 필요한 경우로서 특정 개인을 알아볼 수 없는 형태로 개인정보를 제공하는 경우 등</li>
    </ul>`,
    sort: 4
  },
  {
    title: '개인정보처리의 위탁',
    description: `
    <p>당사는 원활한 업무 처리를 위하여 지원자의 개인정보 처리업무를 외부 전문업체에 일부 위탁하고 있습니다.</p>
    <ul>
    <table>
    <tbody>
    <tr> <th>업체명</th> <td>NICE평가정보(주)</td> </tr>
    <tr> <th>위탁업무</th> <td>채용입사자의 본인인증</td> </tr>
    </tbody>
    </table>
    <br>
    <p>당사는 위탁계약 체결시 위탁업무 수행목적 외 개인정보 처리금지, 기술적·관리적 보호조치, 재위탁 제한, 수탁자에 대한 관리·감독, 손해배상 등에 관한 사항을 계약서 등 문서에 명시하고, 개인정보가 분실·도난·유출·변조 또는 훼손되지 않도록 수탁사를 교육하고, 현황점검 등을 통하여 수탁사가 개인정보를 안전하게 처리하는지를 감독하고 있습니다.</p>`,
    sort: 5
  },
  {
    title: '정보주체의 권리·의무 및 그 행사방법',
    description: `
    <p>귀하는 다음과 같이 본인 및 14세미만 아동(법정대리인만 해당)의 [개인정보 열람·정정·삭제·처리정지]를 요구할 수 있습니다.</p>
    <ul>
    <li>귀하는 언제든지 등록되어 있는 개인정보를 조회하거나 수정할 수 있으며 정보삭제·처리정지를 요구할 수 있습니다. 정보삭제 또는 처리정지를 요구하는 경우 인사팀 담당자에게 서면, 전화 또는 이메일로 연락 하시면 지체 없이 조치하겠습니다.</li>
    <li>입사 지원을 통한 개인정보의 수집·이용에 대하여 동의하신 내용을 철회하실 경우에는 인사팀에 서면, 전화 또는 이메일로 연락하시면 서류접수가 완료된 후 개인정보의 삭제 등 필요한 조치를 하겠습니다.</li>
    </ul>`,
    sort: 6
  },
  {
    title: '처리하는 개인정보의 항목 및 수집방법',
    description: `
    <p>당사는 채용을 위해 필요한 최소한의 개인정보만 수집하고 있으며, 항목은 다음과 같습니다.</p>
    <ul>
    <li>수집항목</li>
    <ul>
    <li>필수적 정보 : 성명, 생년월일, 연락처, 이메일주소, 학력사항</li>
    <li>선택적 정보 : 성별, 주소, 보훈사항(보훈번호 포함), 증명사진, 어학 및 자격사항, 경력사항, 포상내역, 병역사항, 장애인 여부</li>
    </ul>
    <li>수집방법 : 당사 채용 홈페이지(https://shinrecruit.shinyoung.com) <br>※ 당사는 위의 선택정보 수집·이용에 동의하지 않는다는 이유로 귀하에게 채용 지원을 거부할 수 없습니다.</li>
    </ul>`,
    sort: 7
  },
  {
    title: '개인정보 파기절차 및 방법',
    description: `
    <p>당사는 다음과 같은 사유가 발생하는 경우에는 개인정보를 지체 없이 파기합니다. 다만, 법률에 따라 개인정보를 보존해야 하는 경우에는 그러하지 아니합니다.</p>
    <ul>
    <li>동의를 얻은 개인정보의 처리 목적을 달성한 경우</li>
    <li>동의를 얻은 개인정보의 보유 기간이 종료한 경우</li>
    <li>사업을 폐지하는 경우</li>
    </ul>
    <p>종이에 출력된 개인정보는 분쇄기로 분쇄하거나 소각을 통하여 파기하고 전자적 파일 형태로 저장된 개인정보는 복원이 불가능한 방법으로 영구히 삭제합니다.</p>`,
    sort: 8
  },
  {
    title: '개인정보 자동수집 장치 설치·운영 및 그 거부에 관한 사항',
    description: `
    <p>당당사는 귀하의 정보를 저장하고 찾아내는 '세션(Session)'을 운영 합니다. 세션은 로그인이 필요한 경우 자동으로 생성되어 로그인 시간 동안 이용자의 정보를 서버에 저장하고, 로그아웃 시 삭제됩니다. 당사는 개인정보를 자동으로 저장하고 수집하는 장치로서 '쿠키(cookie)'는 사용하고 있지 않습니다.</p>`,
    sort: 9
  },
  {
    title: '개인정보의 안전성 확보조치',
    description: `
    <p>당사는 귀하의 개인정보가 분실·도난·유출·변조 또는 훼손되지 아니하도록 안전성을 확보하기 위하여 다음과 같이 기술적·관리적 및 물리적 조치를 하고 있습니다.</p>
    <ul>
    <li>기술적 조치</li>
    <ul>
    <li>시스템에 대한 접근 권한을 업무 수행에 필요한 최소 범위로 차등 부여</li>
    <li>침해사고 방지를 위한 침입차단 및 침입탐지시스템을 운영</li>
    <li>비밀번호 등 중요한 개인정보에 대한 암호화</li>
    <li>개인정보 문서의 도난/분실에 대응하기 위해 문서암호화 솔루션 적용</li>
    <li>악성프로그램 등을 방지·치료할 수 있는 보안프로그램의 설치·운영</li>
    </ul>
    <li>관리적 조치</li>
    <ul>
    <li>개인정보의 안전한 처리를 위한 내부관리계획의 수립</li>
    <li>개인정보를 직접 취급하는 임직원 및 수탁자 등에 대한 교육계획 수립 및 실시</li>
    <li>내부관리계획에 따른 정기적인 자체 점검의 실시</li>
    </ul>
    <li>물리적 조치</li>
    <ul>
    <li>전산실, 개인정보자료 보관실 등에 대한 출입통제 시스템 운영</li>
    <li>본사 및 각 지점에서 24시간 CCTV 및 보안업체 경비시스템 운영</li>
    <li>개인정보 포함 서류 등은 잠금 장치가 있는 안전한 장소에 보관</li>
    </ul>`,
    sort: 10
  },
  {
    title: '개인정보보호책임자 등',
    description: `
    <p>당사의 개인정보보호책임자 등은 다음과 같습니다.</p>
    <ul>
     <li>정책 관련 개인정보보호책임자</li>
     <ul>
      <li>이름 : 이 시 복</li>
      <li>소속/직위 : 준법감시인</li>
     </ul>
     <li>정보시스템 관련 개인정보보호책임자</li>
     <ul>
      <li>이름 : 원 창 선</li>
      <li>소속/직위 : IT센터 본부장</li>
     </ul>
     <li>개인정보보호 담당부서</li>
     <ul>
      <li>소속 : 정보보호팀</li>
      <li>전화번호 : (02)2004-9000</li>
     </ul>
     <li>정보시스템 관련 개인정보보호책임자</li>
     <ul>
      <li>이름 : 원 창 선</li>
      <li>소속/직위 : IT센터 본부장</li>
     </ul>
     <li>정보시스템 관련 담당부서</li>
     <ul>
      <li>소속 : IT센터</li>
      <li>전화번호 : (02)2004-9000</li>
     </ul>
     <li>채용관련 개인정보보호 담당부서</li>
     <ul>
      <li>소속 : 인사팀</li>
      <li>전화번호 : (02)2004-9000</li>
     </ul>
    </ul>`,
    sort: 11
  },
  {
    title: '정보주체의 권익침해에 대한 구제방법',
    description: `
    <p>개인정보침해에 대한 신고·상담이 필요한 경우 한국인터넷진흥원내 개인정보침해신고센터로 문의하시기 바랍니다.</p>
    <ul>
    <li>개인정보침해신고센터</li>
    <ul>
    <li>전화 : (국번없이)118</li>
    <li>이메일 : 118@kisa.or.kr, privacyclean@kisa.or.kr</li>
    <li>URL : http://privacy.kisa.or.kr</li>
    </ul>`,
    sort: 12
  },
  {
    title: '개인정보 처리방침의 변경',
    description: `
    <p>본 개인정보처리방침은 2022.6.2 부터 적용됩니다.</p>`,
    sort: 13
  },
])

</script>

<style scoped>
.recruitProcedure-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.page-inner {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 98px var(--app-frame-padding-x) 88px;
  /* padding: 42px 20px 88px; */
}

.page-title {
  margin-bottom: 38px;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--tap-text);
}

.policyList {
  border: 1px solid #e3e8e5;
  border-radius: 8px;
  padding: 40px 30px;
}

.policyListItem {
  padding: 10px 0;
}

.policyTitle {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 10px;
}

.html-content :deep(ul){
  margin-top: 5px;
}

.html-content :deep(li){
  margin-top: 5px;
  margin-bottom: 5px;
}

/* =========================
   반응형
========================= */
@media (max-width: 768px) {
  .page-inner {
    padding: 32px 16px 64px;
  }

  .page-title {
    font-size: 30px;
  }
}
</style>
