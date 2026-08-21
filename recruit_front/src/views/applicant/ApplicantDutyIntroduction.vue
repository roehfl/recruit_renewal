<template>
  <section class="duty-introduction-page">
    <section class="quick-link-section">
        <div class="page-inner">
        <ApplicantBreadcrumb />

        <h1 class="page-title">직무소개</h1>

        <section class="main-visual">
            <div class="visual-text">
                <h1>신영증권 직무소개</h1>
                <span
                @click="goRecruits()"
                >입사지원 바로가기</span>
            </div>
        </section>

            <div class="quick-link-grid">
            <a-card
                v-for="item in dutyList"
                :key="item.url"
                class="quick-link-card"
                :bordered="false"
                hoverable
            >
                <div class="card-content">
                <div>
                    <h3 class="card-title">{{ item.title }}</h3>
                    <p class="card-desc">{{ item.eyebrow }}</p>
                </div>

                <div class="card-bottom">
                    <a-button 
                        type="primary"
                        @click="modalOpen(item.url)">
                        상세보기
                    </a-button>
                </div>
                </div>

            </a-card>
            </div>

            </div>
    </section>
  </section>

  <a-modal
      :getContainer="false"
      v-for="item of dutyList"
      :key="item.url"
      v-model:open="modalStatus[item.url]"
      :width="750"
      :closable="false"
      :footer="null"
      @cancel="modalClose(item.url)"
      >
      <DutyIntroModalBody :job="item" @close="modalClose(item.url)" />
   </a-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ApplicantBreadcrumb from '@/views/applicant/ApplicantBreadcrumb.vue'
import { useRouter } from 'vue-router'
import DutyIntroModalBody from '@/views/applicant/DutyIntroModalBody.vue'
import type { DutyItem } from '@/types/duty'

const router = useRouter()

const goRecruits = async (): Promise<void> => {
  await router.push('/applicant/recruits')
}

const modalStatus = ref<Record<string, boolean>>({})

const modalOpen = (url: string): void => {
  modalStatus.value[url] = true
}

const modalClose = (url: string): void => {
  modalStatus.value[url] = false
}

const dutyList = ref<DutyItem[]>([
  {
    url: 'Wealth Management',
    eyebrow: 'Wealth Management Service',
    title: '자산관리 서비스',
    major: '전공 무관',
    degree: '학사 이상',
    intro: 'WM(Wealth Management) 직무는 고객 중심의 자산관리 서비스 제공을 핵심 추진과제로 하며 포트폴리오를 기반으로 한 상품 및 투자 상담, 세무/부동산 컨설팅, 자산승계 등 종합적인 자산관리 서비스를 제공하는 업무를 수행합니다. 고객을 직접 대면하여 소통하는 영업뿐 아니라 다양한 투자안을 분석하고 포트폴리오를 수립하거나, 효과적으로 고객 자산관리를 할 수 있는 다양한 방안을 모색, 기획하는 일을 함께 합니다.',
    skills: [
      { lead: '공감 능력 및 커뮤니케이션 역량:', body: '항상 고객 중심으로 사고하는 직무이므로 무엇보다도 고객 지향 마인드, 고객에 대한 공감 능력 및 유연한 커뮤니케이션 역량이 중요합니다.' },
      { lead: '문제해결 능력:', body: '자산관리 분야는 항상 새로운 과제에 대해 유연하게 사고하고 솔루션을 제공해야 하기 때문에 문제 사항을 잘 이해하고 고민하여 가장 적합한 솔루션을 도출하는 능력과 문제에 대한 도전정신이 필요합니다.' },
      { lead: '윤리의식:', body: '눈앞의 성과보다는 고객에게 최적의 솔루션을 제안하기 위해 높은 수준의 도덕성과 윤리의식이 필요합니다.' },
    ],
    career: '자산관리 분야는 영업뿐만 아니라 다양한 업무를 직간접적으로 경험할 수 있습니다. 고객에게 최적의 솔루션을 제공하기 위해 금융상품 투자뿐만 아니라 세무, 부동산 컨설팅, IB 등 다양한 분야의 지식을 쌓을 수 있고, 이를 통해 자산관리 서비스의 전문가로 성장할 수 있습니다. 다양한 현장 경험은 영업 기획, 전략, 마케팅, 교육 등 여러 분야로의 커리어 확장 기회를 제공합니다.',
    diff: '신영증권 WM 부문은 고객 이해를 기반으로 장기적 관점에서 고객이 필요로 하는 솔루션 제공에 집중합니다. 주식 영업, 금융상품 판매가 아닌, 팀 단위 자산관리와 본사 전문가 협업을 통해 최적의 솔루션을 찾고 이를 통해 고객만족을 높이는 것을 우선 가치로 생각합니다. 신영증권 WM 부문에서는 자산관리에서 승계까지 아우르는 다양한 과정을 경험하고 전문성을 키울 수 있는 기회를 제공하고 있어, 남다른 경쟁력을 가진 인재로 성장할 수 있습니다.',
  },
  {
    url: 'Institutional Sales',
    eyebrow: 'Institutional Sales',
    title: '본사영업',
    major: '무관',
    degree: '학사 이상',
    intro: '본사영업은 기관투자자를 대상으로 하는 영업으로 주식, 채권, 파생상품 등을 중개, 판매하는 영업과 구조화 상품을 발행하여 판매하는 영업이 있습니다. 중개 영업의 주요 고객은 연기금, 운용사, 보험사이며 리서치센터와 협업하여 고객에게 필요한 시장정보와 투자 아이디어도 제공하고 있습니다. 팀 중심 영업활동을 통해 시장 점유율 확대와 수익성 다양화를 목표로 하고 있습니다. 구조화 상품을 판매하는 영업은 국내 및 해외시장에서 거래되는 다양한 기초자산을 다루며, 국내 및 외국계 IB와 주로 거래합니다.',
    skills: [
      { lead: '공감 및 커뮤니케이션 역량:', body: '부서원과 긴밀한 협업이 필요하며, 고객의 니즈를 파악하고 여러 주체의 요구사항을 조율해야 하기 때문에 커뮤니케이션 능력이 중요합니다.' },
      { lead: '금융시장에 대한 이해:', body: '기관투자자 및 국내외 IB를 대상으로 영업하기 때문에 높은 수준의 경제/재무/금융상품/파생상품에 대한 이해가 필요합니다.' },
      { lead: '외국어 역량:', body: '외국계 IB를 대상으로 하는 영업에 있어 외국어 역량은 필수입니다.' },
    ],
    career: '영업의 가장 큰 장점은 대내외로 다양한 네트워크를 쌓을 수 있고, 리서치 및 상품을 공급하는 부서는 물론 대내외 다양한 금융전문가와의 소통으로 지식을 쌓고 견문을 넓힐 수 있다는 것입니다. 전통적인 금융자산뿐만 아니라 구조화된 상품을 접하면서 습득한 지식, 세일즈를 하면서 형성한 네트워크는 향후 금융 전문가로 성장하는데 중요한 자산이 됩니다. 또한 영업을 통해 쌓은 네트워크를 기반으로 기금, 보험사, 공사, 은행 및 제조업의 자금 운용팀, 재무팀, IR팀으로 커리어 전환도 가능합니다.',
    diff: '신영증권은 오랜 기간 기관 세일즈에 강점을 가지고 있는 종합증권사로 금융시장에서 거래하는 모든 상품을 발행, 중개, 판매하고 있습니다. 세일즈 부서 간 협업을 통한 시너지 창출을 오랜 전통으로 여기고 있고 폭넓은 상품을 취급하고 있어, 여러 영역의 세일즈를 경험할 수 있습니다. 오랜시간 고객과 이어진 관계 위에서 새로운 네트워크를 쌓아갈 수 있습니다.',
  },
  {
    url: 'Investment Banking',
    eyebrow: 'Investment Banking',
    title: 'IB',
    major: '경영/회계/경제',
    degree: '학사 이상',
    intro: 'IB는 기업의 자금 수요자와 자금 공급자를 중개하는 업무를 핵심 비즈니스로 하며 자금 조달의 형태에 따라 채권, 대출, 주식, 구조화증권 등과 관련된 부서로 구성되어 있습니다. 자금을 조달하는 전 과정에 참여하여 수요자에게 가장효과적인 조달 방법을 설계하고 자문하는 서비스를 제공하며, 발행된 유가증권 등을 공급자에게 세일즈하는 역할까지 수행합니다. 이 과정에서 양자간의 니즈를 파악하고 조율하며, 거래 구조를 설계하고, 기업과 산업을 숫자로 표현하고 분석하며, 감독원·거래소 등 유관 기관에 대응하는 업무 등을 수행하게 됩니다.',
    skills: [
      { lead: '회계지식과 분석적 사고력:', body: '경제 및 자본시장에 대한 관심과 재무제표를 정확히 해석할 수 있는 회계지식이 필요합니다. 회계/재무 지식에 더하여 분석적 사고를 통해 기업가치를 평가할 수 있어야 합니다.' },
      { lead: '문서 작성 능력:', body: '분석한 내용을 정확하고 신속하게 보고서로 작성할 수 있어야 합니다.' },
      { lead: '커뮤니케이션 역량:', body: '자금 수요자, 투자자, 감독기관 등 다양한 주체의 요구사항을 적절하게 조율하고 쉽게 설명할수 있어야 합니다.' },
      { lead: '문제해결 능력:', body: '기업마다 경영환경과 사업내용이 다르므로 매번 다양한 문제사항에 직면하게 됩니다. 따라서 적극적으로 문제를 해결하려는 자세가 필요합니다.' },
    ],
    career: '재무제표 및 매크로를 분석하고 여러 구조화된 금융상품을 접하면서 기업, 산업, 금융시장에 대한 분석력을 키워 시야를 넓혀갈 수 있습니다. 기업의 CEO, CFO 등 주요 의사결정자와 커뮤니케이션하며 형성한 네트워크는 금융 전문가로 성장할 수 있는 중요한 자산입니다. 향후 Investment Banker로 지속 성장(Sell-side)하거나 운용부서(Buy-side)로 업무를 확장할 수 있습니다. 딜 구조, 투자 상품 등에 내재된 위험과 효익을 분석하고 승인을 내는 심사역, 기업·산업에 대한 분석 보고서를 내는 애널리스트 등으로 직무 전환도 가능합니다.',
    diff: '신영증권은 종합 증권사로 다양한 IB업무(장단기채권, CP, IPO, 구조화증권, 부동산, 인수금융, 기업금융, VC, PE 등)를 영위하고 있습니다. IB업무의 여러 영역을 직·간접적으로 경험해 볼 수 있어, 역량 및 적성에 적합한 분야를 찾아볼 수 있는 기회가 있습니다. 고객과의 신뢰를 지키기 위해 심도 있는 토론과 분석으로 상품을 선별하고 있어 폭넓고 깊이 있는 IB 관련 지식을 함양할 수 있습니다.',
  },
  {
    url: 'Finance for Strategy',
    eyebrow: 'Finance for Strategy',
    title: '운용',
    major: '무관',
    degree: '학사 이상(석사, 박사 우대)',
    intro: '운용은 재원에 따라 고유자산을 운용하는 경우와 외부에서 자금을 조달하여 운용하는 경우로 나눌 수 있습니다. 운용대상이 되는 자산은 주식, 채권, 외환, Commodity 및 관련 파생상품까지 다양합니다. 운용은 금융에 대한 전문적인 지식을 바탕으로 다양한 정보를 수집하여 트레이딩을 하거나 투자 전략을 개발하여 신상품을 기획하고, 신규 투자 대상을 선별하여 발굴하는 일을 합니다.',
    skills: [
      { lead: '금융시장에 대한 관심과 수리적 감각:', body: '국내 및 글로벌 금융시장에 대한 이해와 관심이 높아야 하며, 수리적 감각 및 분석 능력을 갖추고 있어야 합니다.' },
      { lead: '문제해결 능력:', body: '제한된 정보와 시간 속에서 최적의 운용 솔루션을 도출해내기 위한 창의성이 필요하며, 시장에서 벌어지는 예측 불가능한 상황에 냉정하게 판단하고 대처하는 능력이 필요합니다.' },
      { lead: '학습 능력:', body: '다루는 상품이 다양하고 광범위하다 보니 호기심을 가지고 지속적으로 새로운 지식을 익히고 습득해야합니다.' },
    ],
    career: '다양한 밸류에이션 기법과 분석 능력으로 운용 및 관리 노하우를 습득할 수 있습니다. 파생상품 운용을 통해서는 금융 공학, 구조화 상품에 대한 이해를 훈련하게 됩니다. 주어진 한도 내에서 상품을 거래하며 손익 및 리스크 관리 역량을 키우고 종합적인 판단 능력을 익히게 됩니다. 향후 본인의 역량과 적성에 따라 트레이더, 퀀트 전문가, 자산운용역으로 커리어를 선택하여 성장할 수 있으며 PE, VC, 리스크관리 등 다양한 관련 직무로도 확장이 가능합니다.',
    diff: '신영증권은 펀더멘털 분석을 바탕으로 리스크 대비 안정적인 수익을 지향합니다. 모멘텀 투자보다는 시장 리서치, 기업 분석 등을 통해 회사 투자철학에 적합한 투자 및 전략을 활용합니다. 수리적 감각과 분석적 사고능력을 갖추고 있다면 전문지식이 부족하여도 내부 교육 훈련 프로그램을 통해 전문가로 성장할 수 있고, 우리 회사만의 축적된 운용 노하우와 파생상품 모델링 능력을 바탕으로 여러 금융상품에 대한 심도있는 지식을 함양할 수 있습니다.',
  },
  {
    url: 'Research Center',
    eyebrow: 'Research Center',
    title: '리서치센터',
    major: '무관',
    degree: '학사 이상',
    intro: '리서치센터는 개별 자산과 시장, 기업 및 산업분석을 수행하는 조직으로 산업분석팀과 자산전략팀으로 구성되어 있습니다. 산업분석팀은 주요 산업과 관련 기업에 대한 조사분석 업무를 하며, 최근 분석의 범위가 해외 기업까지 확장되고 있습니다. 산업분석팀은 미시적 접근(Bottom-up)을 통한 가치 평가를 지향합니다. 자산전략팀은 주식과 채권, 원자재 등 개별 자산에 대한 투자 의견 제시 및 자산배분 업무를 수행하고 있으며, 거시적 방법론(Top-down)으로 투자 의견을 제시하고 있습니다. 리서치센터 애널리스트는 기업 탐방과 세미나 등 외부 미팅이 많고, 리포트와 동영상 등 다양한 매체로 의견을 개진합니다. 사내 유관 부서, 기관투자자, 개인투자고객, 미디어 등 다양한 이해관계자들이 리서치센터의 고객입니다.',
    skills: [
      { lead: '경제학적 소양 및 재무분석 능력:', body: '다양한 경제 현상을 해석할 수 있는 거시경제 관련 지식과 기업분석 및 실적 추정을 위한 재무, 회계 지식을 기본적으로 갖추어야 합니다.' },
      { lead: '외국어 역량:', body: '해외 시장, 산업, 기업분석 자료를 읽고 해석할 수 있는 수준의 외국어 역량이 필요합니다.' },
      { lead: 'OA 역량:', body: '다양하고 방대한 양의 데이터를 관리하고 분석하기 위해 OA 작업에 능숙해야 합니다.' },
      { lead: '성실, 근면한 태도 및 준법정신:', body: '주어진 시간 내에 업무를 완료하고자 하는 책임감과 강한 체력, 미공개 정보를 주로 다루기 때문에 정보 관련 윤리, 준법정신을 갖추고 있어야 합니다.' },
    ],
    career: '애널리스트로 활동하며 시장과 산업에 대한 통찰을 쌓을 수 있습니다. 또한 다양한 외부고객 및 사내 부서와 협업하므로 본인의 적성과 성과에 따라 중장기적으로는 애널리스트뿐만 아니라 자산운용, IB, 기업체 IR, 기업체 전략/기획 등으로 경력을 확장할 수 있습니다.',
    diff: '신영증권 리서치센터는 오랜 역사와 전통을 가지고 있으며, RA(Research Assistant)로 시작하여 JA(Junior Analyst), SA(Senior Analyst)가 되는 전 과정에서 체계적인 OJT 프로그램을 통해 동료 및 선배 애널리스트들의 경험과 지식 및 노하우를 전수받을 수 있는 기회를 제공하고 있습니다. 또한 틀에 박히지 않은 보고서를 작성할 수 있도록 교육 및 각종 인프라를 제공함으로써 자본시장의 발전에 발맞추어 본인의 역량을 무한 강화할 수 있습니다.',
  },
  {
    url: 'IT Center',
    eyebrow: 'IT Center',
    title: 'IT센터',
    major: '컴퓨터 계열 전공 또는 관련 과목 이수자',
    degree: '학사 이상',
    intro: 'IT는 고객에게 최고의 금융 경험을 제공하고, 직원들에게는 유연한 업무환경을 조성하기 위한 시스템을 개발하고 관리, 운영합니다. 보안시스템 구축을 통해 금융 데이터를 안전하게 지키고, 최신 인프라를 구축하여 안정적인 시스템 운영을 책임집니다. 또한, 고객용 다양한 채널(MTS, HTS, 홈페이지 등) 개발과 더불어 매매/운용/증권업무 관련 시스템 개발, 설계를 통해 금융의 미래를 선도하는 핵심적인 업무를 수행합니다. 기존 시스템의 유지보수뿐 아니라, 최신 기술 트렌드를 반영한 프로젝트 참여를 통해 끊임없이 성장하고 발전할 수 있는 기회를 제공합니다.',
    skills: [
      { lead: '인프라 지식 및 프로그래밍 역량:', body: 'IT 인프라(서버, 네트워크, DBMS 등)에 대한 기초 지식과 프로그래밍 능력이 필요합니다.' },
      { lead: '신기술에 대한 관심 및 자기개발 의지:', body: 'Cloud Computing, Big Data, Block Chain, AI 등 지속적으로 발전하는 신기술에 대해 관심을 갖고 자기개발을 하고자 하는 의지가 있어야 합니다.' },
      { lead: '금융에 대한 관심:', body: '금융 분야에 관심을 가지고 있어야 하며, 금융업을 이해하고자 하는 의지가 필요합니다.' },
      { lead: '커뮤니케이션 역량:', body: '유관기관 및 부서와 많은 협업을 수행하게 되므로 커뮤니케이션 역량, 절차적 사고, 일정 관리 능력 등이 필요합니다.' },
    ],
    career: 'IT 센터는 담당 직무에 따라 크게 두 가지 방향으로 커리어 설계가 가능합니다. 보안, 인프라 관리, 시스템 프로그래밍 등의 직무는 IT에 특화된 전문가로 성장이 가능합니다. 각종 고객 서비스에 대한 시스템 설계부터 개발, 운영과 관련된 직무를 통해서는 증권업에 특화된 IT 전문가로 성장이 가능합니다.',
    diff: '신영증권 IT센터는 업계 최초로 주요 시스템을 클라우드 전환하고 종합신탁 체계를 구축하였으며, 디지털 트랜스포메이션을 위한 다양한 노력으로 업계 선도적인 역할을 수행해오고 있습니다. 새로운 기술에 대한 적극적인 도입과 필요한 기술에 대한 선택과 집중으로 선도적인 역할 수행이 가능한 전문가로 성장할 수 있도록 지원합니다.',
  },
  {
    url: 'Management & Operation',
    eyebrow: 'Management & Operation',
    title: '본사관리',
    major: '무관',
    degree: '학사 이상',
    intro: '증권업 본연의 업무를 수행하기 위해서는 다양한 지원/통제 부서가 필요합니다. 고객에게 제공하는 상품 및 서비스를 발굴하고 판매 전략을 수립하는 부서, 증권 움직임에서 파생되는 매매, 권리, 자금업무 등을 담당하는 부서, 회사의 신규 투자에서 발생 가능한 위험 요소(Risk factor)를 사전에 분석하고, 이를 바탕으로 해당 투자에 대한 합리적인 의사 결정을 지원하는 부서 등이 있습니다. 일반적으로 기업 운영에 요구되는 역할을 하는 부서들도 있습니다. 회사 전반의 경영현황을 점검하고 핵심 의제를 해결하며 전략을 기획하는 부서, 인적자원을 배분 관리하는 부서, 재무자원을 효율적으로 조달·운용하는 부서, 주주총회와 이사회를 관리하고, 경영활동을 지원하는 부서가 이에 해당합니다.',
    skills: [
      { lead: '커뮤니케이션 역량:', body: '유관부서, 대외기관, 감독 당국 등 다양한 주체와 효과적으로 정보와 의견을 교환할 수 있는 역량이 필요합니다.' },
      { lead: '호기심과 통찰력:', body: '증권업과 금융시장에 대한 호기심과 관심이 필요합니다. 이를 바탕으로 데이터를 수집, 가공하고 창의적으로 인사이트를 도출함으로써 효과적인 의사결정을 지원해야 합니다.' },
      { lead: '분석적 사고력 및 적극적 태도:', body: '많은 양의 문서와 데이터를 검토해야 하며, 문제의식과 책임감을 가지고 접근하는 적극적인 마인드가 요구됩니다.' },
      { lead: 'OA 및 외국어 역량:', body: '데이터를 분석하고 이를 정리하기 위해 OA 작업에 능숙해야 하며, 외국 자료를 검토하거나 거래 상대방과의 소통을 위해 기본적인 외국어 역량을 갖추고 있어야 합니다.' },
    ],
    career: '본사관리 부서에서 커리어를 시작하는 경우, 해당 업무에 전문가로 성장할 수도 있지만 외연을 확장하여 더욱 다양한 커리어패스를 설계할 수 있습니다. 대내외 다양한 주체와 커뮤니케이션하여 네트워크를 쌓을 수 있고, 여러 금융상품을 검토하거나 사업 분야를 접할 수 있는 기회가 있습니다. 이를 통해 본인이 가지고 있는 역량을 발굴하고 다양한 방면으로 커리어를 개발할 수 있습니다.',
    diff: '신영증권 본사관리의 차별화된 점은 관심과 호기심, 도전정신만 갖추고 있다면 다양한 업무를 경험해 볼 수 있다는 점입니다. 특정 직무에 국한되어 일하지 않고 부서 간 업무 경계 없이 여러 구성원들이 참여하여 문제를 해결하고 업무를 진행해 나갈 수 있습니다. 이를 통해 다양한 시각과 역량을 가진 사람들과 일해볼 수 있는 기회를 가질 수 있으며, 입체적으로 문제에 접근하고 해결하는 훈련이 가능합니다. 여러 분야의 전문가들과 커뮤니케이션하면서 다양한 일을 직접 경험해 보고 싶은 사람들에게 적합한 포지션입니다.',
  },
])
</script>

<style scoped>
.duty-introduction-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.page-inner {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 42px 20px 42px;
}

.page-title {
  margin: 0;
  /* font-size: 40px;  */
  font-size: 38px; 
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  margin-bottom: 38px;
  color: var(--tap-text);
}

.main-visual {
  height: 180px;
  margin-bottom: 38px;
  border: 1px solid #e3e8e5;
  border-radius: 8px;
  background:
    radial-gradient(circle at 85% 30%, rgba(47, 111, 85, 0.14), transparent 28%),
    linear-gradient(135deg, #ffffff 0%, #f7faf8 55%, #eef6f1 100%);
}

.visual-text {
  padding: 38px 48px;
}

.visual-text p {
  margin: 0 0 10px;
  color: #2f6f55;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.visual-text h1 {
  margin: 0;
  margin-left: 40px;
  color: #20242a;
  font-size: 42px;
  font-weight: 800;
}

.visual-text span {
  display: block;
  margin-top: 12px;
  margin-left: 40px;
  color: #2f6f55;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.7em;
}

.quick-link-section {
  width: 100%;
  /* border: 1px solid var(--app-divider-color);
  border-radius: 10px;
  background: var(--app-bg-surface);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.04);
  padding: 24px; */
}

.section-header {
  margin-bottom: 14px;
}

.section-title {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
}

.section-desc {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.45;
}

.quick-link-grid {
  display: grid;
  max-width: none;
  min-height: 150px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.quick-link-card {
  position: relative;
  overflow: hidden;
  /* min-height: 150px;
  max-width: 330px; */
  border: 1px solid var(--app-border-soft);
  border-top: 4px solid #2f6f55;
  border-radius: 8px;
  background: var(--app-content-bg-color);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.045);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.quick-link-card:hover {
  transform: translateY(-4px);
  border-color: var(--app-color-warning);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}

:deep(.ant-card-body) {
  height: 100%;
  padding: 22px 20px;
}

.card-content {
  position: relative;
  z-index: 2;
  display: flex;
  min-height: 142px;
  flex-direction: column;
  justify-content: space-between;
}

.card-title {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
}

.card-desc {
  margin: 12px 0 0;
  color: var(--app-text-primary);
  font-size: 14px;
  line-height: 1.55;
  word-break: keep-all;
}

.card-bottom {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #5f6f54;
  font-size: 14px;
  font-weight: 600;
}

.go-icon {
  font-size: 12px;
}

@media (max-width: 1100px) {
  .quick-link-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .quick-link-grid {
    grid-template-columns: 1fr;
  }

  .quick-link-card {
    min-height: 160px;
  }

  .card-content {
    min-height: 112px;
  }

  .section-title {
    font-size: 20px;
  }

  .card-title {
    font-size: 18px;
  }
}

:deep(.ant-modal-content) {
  padding: 0;
  overflow: hidden;
  border-radius: 12px;
}

:deep(.ant-modal-body) {
  padding: 0;
}

:deep(.ant-modal-footer) {
  display: none;
}
</style>