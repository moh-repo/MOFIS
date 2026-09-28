package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of the student relationship graph.
 *
 * <p>The tree is assembled server-side and arrives already resolved to the
 * requested language, so the browser never sees a lookup id: a node either has
 * a display {@code value} or it groups children. Depth is not fixed — any node
 * carrying children is expandable — which is what lets a repeating record such
 * as a finance quarter open into its own bank / cheque / status detail.
 */
@Getter
@Setter
public class StudentGraphNodeDto {

    /** Stable within one response; used as the vis-network node id. */
    private String id;

    /** Field or group name, localized. */
    private String label;

    /** Display value for a leaf; null for a grouping node. */
    private String value;

    /** Category this node belongs to, so the client can colour a whole branch. */
    private String category;

    private List<StudentGraphNodeDto> children = new ArrayList<>();

    public StudentGraphNodeDto() {
    }

    public StudentGraphNodeDto(String id, String label, String value, String category) {
        this.id = id;
        this.label = label;
        this.value = value;
        this.category = category;
    }

    public StudentGraphNodeDto add(StudentGraphNodeDto child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
