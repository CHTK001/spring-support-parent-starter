/**
 * 整卷二维码身份编码验收：
 * PaperPreview 在 paperId 之外支持 studentNo / examId，
 * 二维码内容拼接为 PAPER:...;STUDENT:...;EXAM:...，供阅卷端自动绑定与替考校验。
 */
const fs = require('fs');
const path = require('path');

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail ?? '') });
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : '   [' + detail + ']'}`);
};

const source = fs.readFileSync(
  path.join(__dirname, '../../../../vue-support-parent-starter/pages/eduai/src/components/paper/PaperPreview.vue'),
  'utf8'
);

record(
  'PreviewHeader declares studentNo',
  source.includes('studentNo?: string;'),
  'studentNo optional prop'
);
record(
  'PreviewHeader declares examId',
  source.includes('examId?: number | string | null;'),
  'examId optional prop'
);

const qrFnStart = source.indexOf('const paperQr = ref("");');
const qrFnEnd = source.indexOf('watch(', qrFnStart);
const qrBody = qrFnStart >= 0 && qrFnEnd > qrFnStart ? source.slice(qrFnStart, qrFnEnd) : '';
record(
  '二维码内容包含 STUDENT 段',
  qrBody.includes('parts.push(`STUDENT:${studentNo}`)'),
  'STUDENT segment appended'
);
record(
  '二维码内容包含 EXAM 段',
  qrBody.includes('parts.push(`EXAM:${examId}`)'),
  'EXAM segment appended'
);
record(
  '二维码以 PAPER 开头并用分号拼接',
  qrBody.includes('const parts = [`PAPER:${paperId}`]') && qrBody.includes('parts.join(";")'),
  'PAPER + semicolon segments'
);
record(
  'studentNo/examId 变化会重绘二维码',
  source.includes('props.header.studentNo, props.header.examId'),
  'watch key includes identity fields'
);

const failed = checks.filter((c) => !c.ok).length;
console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
process.exit(failed === 0 ? 0 : 3);