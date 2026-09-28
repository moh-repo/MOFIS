export class StudyWafarType {
  id: number;
  wafarName: string;
  /** WAFAR_NAME_EN. The dialog never sent it, and the server clears what it is not sent. */
  wafarNameEn: string | null;
  wafarNote1: string | null;
  swafarMidate: string | null;

  constructor() {
    this.id = 0;
    this.wafarName = '';
    this.wafarNameEn = null;
    this.wafarNote1 = null;
    this.swafarMidate = null;
  }
}
