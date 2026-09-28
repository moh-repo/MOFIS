import { HierarchyExportNode, MAX_BOXES_PER_ROW, drawHierarchyPicture, hierarchyTable } from './hierarchy-export.util';

/**
 * R5-11: the Hierarchical Charts export hung the tab. The page rasterised its
 * organization charts with html2canvas, and the academic one — some two hundred
 * majors, each with a status and a gender under it — is ~8,700 elements and
 * ~34,000 px wide. These pin the replacement: a picture and a table made from the
 * data, bounded in size and cost however large the tree is.
 */

const NBSP4 = ' '.repeat(4);

/** The shape that hung the export: `majors` majors, each with one status and one gender. */
function academicLike(majors: number): HierarchyExportNode {
    const children = Array.from({ length: majors }, (_, i) => {
        const value = majors - i;
        return {
            label: `Major ${i + 1}`,
            value,
            children: [{ label: 'Active', value, children: [{ label: 'Male', value }] }]
        };
    });
    return { label: 'Academic Distribution', value: children.reduce((sum, c) => sum + c.value, 0), children };
}

function distinctColors(canvas: HTMLCanvasElement): number {
    const { data } = canvas.getContext('2d')!.getImageData(0, 0, canvas.width, canvas.height);
    const seen = new Set<string>();
    for (let i = 0; i < data.length; i += 4) {
        seen.add(`${data[i]},${data[i + 1]},${data[i + 2]}`);
    }
    return seen.size;
}

describe('hierarchy export (R5-11)', () => {

    describe('hierarchyTable', () => {

        it('lists every node once, parents first, indented by depth', () => {
            const table = hierarchyTable({
                label: 'All Countries',
                value: 10,
                children: [
                    { label: 'Egypt', value: 6, children: [{ label: 'Cairo', value: 6 }] },
                    { label: 'Jordan', value: 4 }
                ]
            }, 'Segment', 'Students');

            expect(table.columns).toEqual(['Segment', 'Students']);
            expect(table.rows).toEqual([
                ['All Countries', '10'],
                [`${NBSP4}Egypt`, '6'],
                [`${NBSP4}${NBSP4}Cairo`, '6'],
                [`${NBSP4}Jordan`, '4']
            ]);
        });

        it('carries the whole 547-node tree QA exported, not a slice of it', () => {
            const table = hierarchyTable(academicLike(182), 'Segment', 'Students');

            expect(table.rows.length).toBe(547);
            expect(table.rows[table.rows.length - 1][0]).toBe(`${NBSP4}${NBSP4}${NBSP4}Male`);
        });

        it('prints counts with Western digits, grouped like the other chart tables', () => {
            const table = hierarchyTable({ label: 'Root', value: 12345 }, 'Segment', 'Students');

            expect(table.rows[0][1]).toBe('12,345');
        });
    });

    describe('drawHierarchyPicture', () => {

        it('keeps a two-hundred-branch tree to one row of boxes and folds the rest', () => {
            const moreLabel = jasmine.createSpy('moreLabel').and.returnValue('175 more');

            const picture = drawHierarchyPicture(academicLike(182), { moreLabel, scale: 1 });

            // Seven majors are drawn; the other 175 become one box.
            expect(moreLabel).toHaveBeenCalledOnceWith(182 - (MAX_BOXES_PER_ROW - 1));
            expect(picture.width).toBeLessThanOrEqual(MAX_BOXES_PER_ROW * (150 + 18) - 18 + 32);
            expect(picture.canvas.width).toBe(picture.width);
        });

        it('draws a small tree whole, down to its last level', () => {
            const moreLabel = jasmine.createSpy('moreLabel');
            const flat = drawHierarchyPicture({ label: 'Root', value: 2, children: [{ label: 'A', value: 2 }] },
                { moreLabel, scale: 1 });
            const deep = drawHierarchyPicture(academicLike(3), { moreLabel, scale: 1 });

            expect(moreLabel).not.toHaveBeenCalled();
            // Root, majors, statuses, genders: four rows of boxes against two.
            expect(deep.height).toBeGreaterThan(flat.height);
        });

        it('takes milliseconds for the tree that used to hold the tab for minutes', () => {
            const started = performance.now();
            drawHierarchyPicture(academicLike(182), { moreLabel: (n) => `${n} more`, scale: 2 });
            hierarchyTable(academicLike(182), 'Segment', 'Students');

            expect(performance.now() - started).withContext('ms for 547 nodes').toBeLessThan(500);
        });

        it('draws an Arabic tree right to left', () => {
            const picture = drawHierarchyPicture(
                { label: 'جميع الدول', value: 3, children: [{ label: 'مصر', value: 2 }, { label: 'الأردن', value: 1 }] },
                { moreLabel: (n) => `${n} أخرى`, rtl: true, scale: 1 }
            );

            expect(distinctColors(picture.canvas)).toBeGreaterThan(2);
        });
    });
});
