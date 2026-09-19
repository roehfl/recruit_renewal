package com.shinyoung.recruit.service;

/** 메일 1통의 내용. html 은 고정 레이아웃을 입힌 본문, text 는 같은 내용의 일반 텍스트. */
public record MailMessage(String fromName, String fromAddress, String subject, String html, String text) {
}
