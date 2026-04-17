// EN + RU adult content trigger words for DOM text scanning.
// Stems used where possible to cover inflections (e.g. "порно" covers "порнография").

export const TRIGGER_WORDS_EN: readonly string[] = [
  // Explicit
  'porn', 'pornography', 'xxx', 'nude', 'naked', 'nudity',
  'masturbat', 'orgasm', 'ejaculat', 'erection', 'genital',
  'penis', 'vagina', 'vulva', 'anus', 'anal', 'oral sex',
  'blowjob', 'handjob', 'cumshot', 'creampie', 'gangbang',
  'threesome', 'foursome', 'orgy', 'bdsm', 'bondage', 'fetish',
  'dominatrix', 'hentai', 'tentacle',
  // Semi-explicit
  'sex video', 'sex tape', 'sex scene', 'adult video', 'adult content',
  'nsfw', 'onlyfans', 'camgirl', 'cam girl', 'webcam sex',
  'stripper', 'striptease', 'lap dance', 'escort service',
  // Industry terms
  'pornstar', 'porn star', 'adult film', 'adult movie',
  'erotic', 'erotica', 'explicit content',
]

export const TRIGGER_WORDS_RU: readonly string[] = [
  // Явный контент
  'порно', 'порнография', 'порнофильм',
  'голая', 'голый', 'обнажён', 'нагой', 'нагая',
  'мастурб', 'онанизм', 'оргазм', 'эякуляц',
  'половой член', 'пенис', 'вагина', 'влагалище', 'анал',
  'минет', 'куннилинг', 'фелляц',
  // Полуявный
  'секс видео', 'секс-видео', 'порнуха', 'порнушка',
  'эротика', 'эротический', 'жёсткое', 'хардкор',
  'бдсм', 'фетиш', 'доминация', 'хентай',
  // Индустрия
  'стриптиз', 'стриптизёрша', 'эскорт', 'проститутк',
  'интим услуги', 'интим-услуги', 'вебкам', 'онлифанс',
  'порноактрис', 'порноактёр',
]

export const ALL_TRIGGER_WORDS = [...TRIGGER_WORDS_EN, ...TRIGGER_WORDS_RU]
