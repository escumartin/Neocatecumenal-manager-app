import { membersText } from '@/i18n/es/members';
import type { MemberDataExport } from '@/lib/api/members-api';

const text = membersText.dataExport;

type ConsentRecord = MemberDataExport['consentRecords'][number];

function twoDigits(value: number): string {
  return String(value).padStart(2, '0');
}

function dateOf(instant: string): string {
  const date = new Date(instant);
  return `${twoDigits(date.getUTCDate())}/${twoDigits(date.getUTCMonth() + 1)}/${date.getUTCFullYear()}`;
}

function authorOf(recordedBy: ConsentRecord['recordedBy']): string {
  if (recordedBy.role === 'MEMBER') {
    return text.byMember;
  }
  const name = recordedBy.deleted ? text.deletedUser : (recordedBy.name ?? text.deletedUser);
  return `anotado por ${name} (${text.role[recordedBy.role] ?? recordedBy.role})`;
}

function consentLine(record: ConsentRecord): string {
  return `- ${text.field[record.field]}: ${text.decision[record.decision]}, ${text.channel[record.channel]}, ${dateOf(
    record.recordedAt,
  )}, ${authorOf(record.recordedBy)}`;
}

function valueLine(label: string, value: string | undefined): string {
  return `${label}: ${value ?? text.notShared}`;
}

export function dataExportText(data: MemberDataExport): string {
  const { member } = data;
  const consents = data.consentRecords.length > 0 ? data.consentRecords.map(consentLine) : [text.noConsents];
  return [
    text.heading([member.givenName, member.surnames].filter(Boolean).join(' ')),
    text.community(data.communityNumber),
    text.date(dateOf(data.exportedAt)),
    '',
    valueLine(text.field.MOBILE, member.mobile),
    valueLine(text.field.LANDLINE, member.landline),
    valueLine(text.field.EMAIL, member.email),
    '',
    ...(data.consentRecords.length > 0 ? [text.consentsTitle] : []),
    ...consents,
    '',
    membersText.contact.notice.title,
    ...membersText.contact.notice.paragraphs,
  ].join('\n');
}
