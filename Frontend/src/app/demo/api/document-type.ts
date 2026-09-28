export class DocumentType {
    documentTypeId: number;
    documentTypeName: string;
    documentTypeDescription: string;
    isActive: boolean;
    sortOrder: number;

    constructor() {
        this.documentTypeId = 0;
        this.documentTypeName = '';
        this.documentTypeDescription = '';
        this.isActive = true;
        this.sortOrder = 0;
    }
}
