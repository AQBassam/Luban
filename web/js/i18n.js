/* Luban — interface strings (Arabic, English). The screen follows settings.lang. */
(function (root) {
  'use strict';
  const T = {
    ar: {
      fajr: 'الفجر', sunrise: 'الشروق', dhuhr: 'الظهر', jumuah: 'الجمعة', asr: 'العصر', maghrib: 'المغرب', isha: 'العشاء',
      am: 'ص', pm: 'م', noon: 'الزوال', midnight: 'منتصف الليل', in: 'بعد', iqamaIn: 'الإقامة بعد', now: 'الآن',
      min: n => n === 1 ? 'دقيقة' : n === 2 ? 'دقيقتان' : n <= 10 ? 'دقائق' : 'دقيقة',
      hr: n => n === 1 ? 'ساعة' : n === 2 ? 'ساعتان' : n <= 10 ? 'ساعات' : 'ساعة',
      day: n => n === 1 ? 'يوم' : n === 2 ? 'يومان' : n <= 10 ? 'أيام' : 'يوماً',
      verse: 'آية اليوم', hadith: 'حديث اليوم', prayersToday: 'مواقيت اليوم', forecast: 'الأيام القادمة',
      matches: 'مباريات فرقك', matchesSrc: 'البيانات: football-data.org (النتائج متأخرة قليلاً)', shows: 'جديد المسلسلات هذا الأسبوع', noData: 'لا تتوفر بيانات الآن',
      noMatches: 'لا مباريات قريبة', noShows: 'لا جديد هذا الأسبوع', iqama: 'الإقامة',
      nationalDayIn: n => `العيد الوطني العُماني بعد ${n}`, nationalDay: 'كل عام وعُمان بخير — العيد الوطني',
      khareef: 'موسم خريف ظفار', ramadanDay: n => `رمضان — اليوم ${n}`, ramadanIn: n => `رمضان بعد ${n}`,
      eidFitr: 'عيد الفطر مبارك', eidAdha: 'عيد الأضحى مبارك', arafah: 'يوم عرفة', friday: 'جمعة مباركة',
      gold: 'الذهب ر.ع/غ', brent: 'برنت $', btc: 'بيتكوين $', spx: 'S&P 500', live: 'مباشر', ft: 'انتهت',
      today: 'اليوم', tomorrow: 'غداً', photo: 'تصوير',
      weather: {0:'صحو',1:'صحو غالباً',2:'غائم جزئياً',3:'غائم',45:'ضباب',48:'ضباب',51:'رذاذ',53:'رذاذ',55:'رذاذ',
        61:'مطر خفيف',63:'مطر',65:'مطر غزير',66:'مطر متجمد',67:'مطر متجمد',71:'ثلج',73:'ثلج',75:'ثلج كثيف',
        80:'زخات مطر',81:'زخات مطر',82:'زخات غزيرة',95:'عاصفة رعدية',96:'عاصفة رعدية',99:'عاصفة رعدية'},
      dust: 'غبار',
    },
    en: {
      fajr: 'Fajr', sunrise: 'Sunrise', dhuhr: 'Dhuhr', jumuah: "Jumu'ah", asr: 'Asr', maghrib: 'Maghrib', isha: 'Isha',
      noon: 'Noon', midnight: 'Midnight', am: 'AM', pm: 'PM', in: 'in', iqamaIn: 'Iqama in', now: 'now',
      min: n => n === 1 ? 'min' : 'min', hr: n => n === 1 ? 'hr' : 'hr', day: n => n === 1 ? 'day' : 'days',
      verse: 'Verse of the day', hadith: 'Hadith of the day', prayersToday: "Today's prayers", forecast: 'Next days',
      matchesSrc: 'Data: football-data.org (scores slightly delayed)', matches: 'Your teams', shows: 'New episodes this week', noData: 'No data right now',
      noMatches: 'No upcoming matches', noShows: 'Nothing new this week', iqama: 'Iqama',
      nationalDayIn: n => `Oman National Day in ${n}`, nationalDay: 'Happy National Day, Oman',
      khareef: 'Dhofar Khareef season', ramadanDay: n => `Ramadan — day ${n}`, ramadanIn: n => `Ramadan in ${n}`,
      eidFitr: 'Eid al-Fitr Mubarak', eidAdha: 'Eid al-Adha Mubarak', arafah: 'Day of Arafah', friday: "Jumu'ah Mubarak",
      gold: 'Gold OMR/g', brent: 'Brent $', btc: 'Bitcoin $', spx: 'S&P 500', live: 'LIVE', ft: 'FT',
      today: 'Today', tomorrow: 'Tomorrow', photo: 'Photo',
      weather: {0:'Clear',1:'Mostly clear',2:'Partly cloudy',3:'Cloudy',45:'Fog',48:'Fog',51:'Drizzle',53:'Drizzle',55:'Drizzle',
        61:'Light rain',63:'Rain',65:'Heavy rain',66:'Freezing rain',67:'Freezing rain',71:'Snow',73:'Snow',75:'Heavy snow',
        80:'Showers',81:'Showers',82:'Heavy showers',95:'Thunderstorm',96:'Thunderstorm',99:'Thunderstorm'},
      dust: 'Dust',
    },
  };
  root.LubanI18n = { T };
})(typeof window !== 'undefined' ? window : globalThis);
