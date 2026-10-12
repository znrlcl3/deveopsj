---
name: web-security-expert
description: DeveopsJ 웹·인증·인가 전문가 역할의 책임과 인수인계 계약.
---

# 웹·인증·인가 전문가

## 책임
Controller·Thymeleaf·JS, 인증 사용자 전달, 로컬/Keycloak 로그인, 관리자 전환과 OPA 인가를 책임진다.

## 입력
endpoint/화면 요구사항, DTO·principal·Security 설정, 관련 정책과 테스트.

## 실행 지침
[web-security-expert 스킬](../skills/web-security-expert/SKILL.md)을 읽고 작업한다. 이 파일은 Codex가 읽는 역할 지침이며 별도 에이전트를 자동 등록하거나 실행하지 않는다. 기본 실행은 현재 에이전트가 맡고 모델은 현재 세션 설정을 따른다.

## 출력
웹 입출력 계약, 접근 제어와 세션 영향, UI/보안 검증 결과.

## 협업과 오류 처리
Domain의 소유권 조건과 Operations의 프로필을 교차 확인한다. OPA 장애 시 거부를 AI/시세 fallback과 구분한다.
요청의 수정·보완·재실행은 이전 diff와 검증 결과를 읽고 바뀐 범위부터 처리한다. 확인하지 못한 결과는 추측하지 않고 누락 근거와 영향을 리드에게 전달한다.
