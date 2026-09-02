const RULES = [
  { pattern: /\b(generate|create|make)\s+(a\s+)?video\b|\banimate\b|video\s+of\b/i, type: 'VIDEO_GENERATION' },
  { pattern: /\b(generate|create|make|draw|paint|illustrate|design)\s+(a\s+)?(image|picture|photo|illustration|art)\b|\bpicture\s+of\b|\bphoto\s+of\b|\bimage\s+of\b/i, type: 'IMAGE_GENERATION' },
  { pattern: /\b(generate|create)\s+(a\s+)?audio\b|\btext\s+to\s+speech\b|\btts\b|\bvoiceover\b|\bspeech\b|\bnarrate\b/i, type: 'AUDIO_GENERATION' },
  { pattern: /\.pdf\b|\bpdf\s+document\b|\bcreate\s+a\s+pdf\b|\bexport\s+as\s+pdf\b/i, type: 'DOCUMENT_PDF' },
  { pattern: /\.docx\b|\bword\s+document\b|\bdoc\s+file\b|\bcreate\s+a\s+doc\b/i, type: 'DOCUMENT_DOCX' },
  { pattern: /\.pptx\b|\bpresentation\b|\bslide\s+deck\b|\bpowerpoint\b|\bslides\b/i, type: 'DOCUMENT_PPTX' },
  { pattern: /\.xlsx\b|\bspreadsheet\b|\bexcel\b|\bcsv\s+export\b/i, type: 'DOCUMENT_XLSX' },
  { pattern: /\b(write\s+code|code\s+for|debug|fix\s+this\s+code|refactor|programming|implement|create\s+a\s+function)\b/i, type: 'CODE_GENERATION' },
  { pattern: /\b(search|browse|find\s+online|what\s+is\s+happening|latest\s+news|research|look\s+up)\b/i, type: 'WEB_SEARCH' }
];

export function classifyRequest(prompt) {
  if (!prompt || typeof prompt !== 'string') return 'GENERAL_TEXT';

  for (const rule of RULES) {
    if (rule.pattern.test(prompt)) return rule.type;
  }
  return 'GENERAL_TEXT';
}
