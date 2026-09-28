import {
    Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SliderModule } from 'primeng/slider';
import { TooltipModule } from 'primeng/tooltip';
import { TranslationService } from '../../service/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

/**
 * Output geometry.
 *
 * 35 x 45 mm is the ISO/ICAO identity-photo format used for ID cards and official forms.
 * Rendering at 600 x 771 px puts the result at roughly 435 dpi, comfortably above the
 * 300 dpi print floor, so the same file stays sharp on a card and in a printed form
 * without being large enough to strain the upload limit.
 */
const OUTPUT_WIDTH = 600;
const OUTPUT_HEIGHT = 771;
const OUTPUT_QUALITY = 0.92;

/** Viewport preserves the output aspect ratio so what is framed is what is produced. */
const VIEWPORT_WIDTH = 300;
const VIEWPORT_HEIGHT = Math.round(VIEWPORT_WIDTH * (OUTPUT_HEIGHT / OUTPUT_WIDTH));

const MIN_ZOOM = 1;
const MAX_ZOOM = 5;

@Component({
    selector: 'app-photo-cropper',
    imports: [
        CommonModule, FormsModule, DialogModule, ButtonModule,
        SliderModule, TooltipModule, TranslatePipe
    ],
    templateUrl: './photo-cropper.component.html',
    styleUrls: ['./photo-cropper.component.scss']
})
export class PhotoCropperComponent implements OnDestroy {

    @ViewChild('viewport') viewportRef?: ElementRef<HTMLDivElement>;

    /** Source picture to frame. Setting a file opens the dialog. */
    @Input() set source(file: File | null) {
        this._sourceFile = file;
        if (file) {
            this.load(file);
        }
    }

    @Output() cropped = new EventEmitter<{ file: File; dataUrl: string }>();
    @Output() cancelled = new EventEmitter<void>();

    visible = false;
    imageSrc: string | null = null;
    /** Percentage for the zoom slider; kept separate from the scale factor it maps to. */
    zoomPercent = 0;
    processing = false;

    readonly viewportWidth = VIEWPORT_WIDTH;
    readonly viewportHeight = VIEWPORT_HEIGHT;

    private _sourceFile: File | null = null;
    private image = new Image();
    private objectUrl: string | null = null;
    /** Scale at which the image exactly covers the viewport — the zoom floor. */
    private baseScale = 1;
    private scale = 1;
    private offsetX = 0;
    private offsetY = 0;
    private dragging = false;
    private dragStartX = 0;
    private dragStartY = 0;
    private dragOriginX = 0;
    private dragOriginY = 0;
    private moveHandler?: (e: MouseEvent | TouchEvent) => void;
    private upHandler?: () => void;

    constructor(public translationService: TranslationService) {}

    get imageTransform(): string {
        return `translate(${this.offsetX}px, ${this.offsetY}px) scale(${this.scale})`;
    }

    /**
     * Live preview of the framed area, scaled to the requested box width.
     *
     * The frame's geometry is expressed in viewport pixels, so every length is multiplied
     * by the box-to-viewport ratio — without that the preview would show the frame's
     * top-left corner at full size instead of the whole crop.
     */
    previewStyle(boxWidth: number): { [key: string]: string } {
        const ratio = boxWidth / VIEWPORT_WIDTH;
        const width = this.image.naturalWidth * this.scale * ratio;
        const height = this.image.naturalHeight * this.scale * ratio;
        return {
            'background-image': this.imageSrc ? `url("${this.imageSrc}")` : 'none',
            'background-size': `${width}px ${height}px`,
            'background-position': `${this.offsetX * ratio}px ${this.offsetY * ratio}px`,
            'background-repeat': 'no-repeat'
        };
    }

    /** Widths must match the preview boxes in the stylesheet. */
    readonly avatarPreviewWidth = 84;
    readonly cardPreviewWidth = 78;

    private load(file: File): void {
        this.releaseObjectUrl();
        this.objectUrl = URL.createObjectURL(file);
        this.imageSrc = this.objectUrl;
        this.image = new Image();
        this.image.onload = () => this.fitToViewport();
        this.image.src = this.objectUrl;
        this.visible = true;
    }

    /** Scales the image to cover the frame and centres it — the starting position. */
    private fitToViewport(): void {
        const { naturalWidth: iw, naturalHeight: ih } = this.image;
        if (!iw || !ih) {
            return;
        }
        this.baseScale = Math.max(VIEWPORT_WIDTH / iw, VIEWPORT_HEIGHT / ih);
        this.scale = this.baseScale;
        this.zoomPercent = 0;
        this.offsetX = (VIEWPORT_WIDTH - iw * this.scale) / 2;
        this.offsetY = (VIEWPORT_HEIGHT - ih * this.scale) / 2;
        this.clampOffsets();
    }

    /** Keeps the frame fully covered, so a crop can never include empty space. */
    private clampOffsets(): void {
        const width = this.image.naturalWidth * this.scale;
        const height = this.image.naturalHeight * this.scale;
        this.offsetX = Math.min(0, Math.max(VIEWPORT_WIDTH - width, this.offsetX));
        this.offsetY = Math.min(0, Math.max(VIEWPORT_HEIGHT - height, this.offsetY));
    }

    onZoomChange(): void {
        const target = this.baseScale * (1 + (this.zoomPercent / 100) * (MAX_ZOOM - MIN_ZOOM));
        this.zoomAroundCentre(target);
    }

    onWheel(event: WheelEvent): void {
        event.preventDefault();
        const step = event.deltaY < 0 ? 4 : -4;
        this.zoomPercent = Math.min(100, Math.max(0, this.zoomPercent + step));
        this.onZoomChange();
    }

    /** Zooms about the frame's centre so the subject does not drift while scaling. */
    private zoomAroundCentre(nextScale: number): void {
        const centreX = VIEWPORT_WIDTH / 2;
        const centreY = VIEWPORT_HEIGHT / 2;
        const imageX = (centreX - this.offsetX) / this.scale;
        const imageY = (centreY - this.offsetY) / this.scale;
        this.scale = nextScale;
        this.offsetX = centreX - imageX * this.scale;
        this.offsetY = centreY - imageY * this.scale;
        this.clampOffsets();
    }

    startDrag(event: MouseEvent | TouchEvent): void {
        if (!this.imageSrc) {
            return;
        }
        event.preventDefault();
        const point = this.pointOf(event);
        this.dragging = true;
        this.dragStartX = point.x;
        this.dragStartY = point.y;
        this.dragOriginX = this.offsetX;
        this.dragOriginY = this.offsetY;

        this.moveHandler = (e) => this.onDrag(e);
        this.upHandler = () => this.endDrag();
        document.addEventListener('mousemove', this.moveHandler as EventListener);
        document.addEventListener('mouseup', this.upHandler);
        document.addEventListener('touchmove', this.moveHandler as EventListener, { passive: false });
        document.addEventListener('touchend', this.upHandler);
    }

    private onDrag(event: MouseEvent | TouchEvent): void {
        if (!this.dragging) {
            return;
        }
        event.preventDefault();
        const point = this.pointOf(event);
        this.offsetX = this.dragOriginX + (point.x - this.dragStartX);
        this.offsetY = this.dragOriginY + (point.y - this.dragStartY);
        this.clampOffsets();
    }

    private endDrag(): void {
        this.dragging = false;
        if (this.moveHandler) {
            document.removeEventListener('mousemove', this.moveHandler as EventListener);
            document.removeEventListener('touchmove', this.moveHandler as EventListener);
        }
        if (this.upHandler) {
            document.removeEventListener('mouseup', this.upHandler);
            document.removeEventListener('touchend', this.upHandler);
        }
    }

    private pointOf(event: MouseEvent | TouchEvent): { x: number; y: number } {
        if ('touches' in event && event.touches.length) {
            return { x: event.touches[0].clientX, y: event.touches[0].clientY };
        }
        const mouse = event as MouseEvent;
        return { x: mouse.clientX, y: mouse.clientY };
    }

    /**
     * Rotates by baking the turn into a new source image rather than tracking an angle.
     * Keeping the displayed image always upright means the pan and zoom maths stay in one
     * orientation, which is where rotating croppers usually go wrong.
     */
    rotate(): void {
        if (!this.imageSrc || !this.image.naturalWidth) {
            return;
        }
        const canvas = document.createElement('canvas');
        canvas.width = this.image.naturalHeight;
        canvas.height = this.image.naturalWidth;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
            return;
        }
        ctx.translate(canvas.width / 2, canvas.height / 2);
        ctx.rotate(Math.PI / 2);
        ctx.drawImage(this.image, -this.image.naturalWidth / 2, -this.image.naturalHeight / 2);

        const rotated = canvas.toDataURL('image/png');
        this.releaseObjectUrl();
        this.imageSrc = rotated;
        this.image = new Image();
        this.image.onload = () => this.fitToViewport();
        this.image.src = rotated;
    }

    reset(): void {
        this.fitToViewport();
    }

    apply(): void {
        if (!this.imageSrc || !this.image.naturalWidth) {
            return;
        }
        this.processing = true;

        const canvas = document.createElement('canvas');
        canvas.width = OUTPUT_WIDTH;
        canvas.height = OUTPUT_HEIGHT;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
            this.processing = false;
            return;
        }

        // JPEG has no alpha; without this, transparent PNG areas would render black.
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT);
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';

        // The frame maps back to this rectangle in the source image.
        const sourceX = -this.offsetX / this.scale;
        const sourceY = -this.offsetY / this.scale;
        const sourceWidth = VIEWPORT_WIDTH / this.scale;
        const sourceHeight = VIEWPORT_HEIGHT / this.scale;

        ctx.drawImage(
            this.image,
            sourceX, sourceY, sourceWidth, sourceHeight,
            0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT
        );

        const dataUrl = canvas.toDataURL('image/jpeg', OUTPUT_QUALITY);
        canvas.toBlob(
            (blob) => {
                this.processing = false;
                if (!blob) {
                    return;
                }
                const name = this.buildFileName();
                const file = new File([blob], name, { type: 'image/jpeg' });
                this.cropped.emit({ file, dataUrl });
                this.close();
            },
            'image/jpeg',
            OUTPUT_QUALITY
        );
    }

    private buildFileName(): string {
        const original = this._sourceFile?.name || 'photo';
        const base = original.includes('.') ? original.substring(0, original.lastIndexOf('.')) : original;
        return `${base}_id.jpg`;
    }

    cancel(): void {
        this.cancelled.emit();
        this.close();
    }

    onHide(): void {
        this.releaseObjectUrl();
    }

    private close(): void {
        this.visible = false;
        this.releaseObjectUrl();
    }

    private releaseObjectUrl(): void {
        if (this.objectUrl) {
            URL.revokeObjectURL(this.objectUrl);
            this.objectUrl = null;
        }
    }

    ngOnDestroy(): void {
        this.endDrag();
        this.releaseObjectUrl();
    }
}
