// 텍스트를 클립보드에 복사한다.
// clipboard API 는 보안 컨텍스트(https/localhost)에서만 제공되므로 없으면 execCommand 로 대체한다.
export const copyText = async (text: string): Promise<void> => {
  if (navigator.clipboard) {
    await navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}
