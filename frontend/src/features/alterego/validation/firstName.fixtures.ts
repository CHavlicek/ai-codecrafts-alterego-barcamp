/**
 * 30-name happy-path corpus shared with the backend sibling at
 * `backend/src/test/resources/firstname-happy-path.txt`. Same content, same
 * order — so a one-sided rule change that accidentally rejects an ordinary
 * name will be visible in the diff and (because both sides assert this
 * corpus) will fail the suite.
 *
 * Mix: ASCII, accented Latin, hyphenated, apostrophed, CJK, Cyrillic,
 * Greek, Arabic.
 */
export const FIRST_NAME_HAPPY_PATH_CORPUS: readonly string[] = [
  'Paula',
  'Renée',
  'José',
  'Søren',
  'Anne-Marie',
  "O'Neil",
  'Mary Jane',
  'Jean-Luc',
  'Björk',
  'Müller',
  'François',
  'Léa',
  'Niamh',
  'Siobhán',
  'Kübra',
  'Ælfred',
  'Nikola',
  'Aleksandr',
  '李娜',
  '王伟',
  '张敏',
  '山田',
  '佐藤',
  '鈴木',
  'محمد',
  'علي',
  'فاطمة',
  'Иван',
  'Дмитрий',
  'Παντελής',
] as const
