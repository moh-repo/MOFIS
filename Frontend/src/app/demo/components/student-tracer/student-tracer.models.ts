export interface NetworkGraph {
    nodes: Node[];
    edges: Edge[];
    stats: NetworkStats;
    /** Students matching the filter; larger than `nodes` when the graph was capped. */
    matchedNodeCount?: number;
    /** True when the server cut the graph to its cap (review item P6). */
    truncated?: boolean;
}

export interface Node {
    id: number;
    studentId: number;
    label: string;
    title?: string;
    group?: string;
    color?: string;
    size?: number;
    shape?: string;
    x?: number;
    y?: number;
    
    // Additional details
    university?: string;
    major?: string;
    graduationYear?: number;
    degreeLevel?: string;
    country?: string;
    employmentStatus?: string;
    currentPosition?: string;
    currentCompany?: string;
    email?: string;
    phone?: string;
    linkedinUrl?: string;
    connectionCount?: number;
}

export interface Edge {
    id: number;
    from: number;
    to: number;
    label?: string;
    title?: string;
    width?: number;
    color?: string;
    connectionType?: string;
    connectionStrength?: number;
    dashes?: boolean | number[] | any; // Allow any to handle backend string
}

export interface NetworkStats {
    totalNodes: number;
    totalConnections: number;
    employedCount: number;
    avgConnectionStrength: number;
    mostCommonConnectionType: string;
    totalUniversities: number;
    totalMajors: number;
    totalCountries: number;
}

export interface TracerFilter {
    university?: string | null;
    major?: string | null;
    country?: string | null;
    employmentStatus?: string | null;
    degreeLevel?: string | null;
    connectionType?: string | null;
    minConnectionStrength?: number | null;
}

export interface FilterOptions {
    universities: string[];
    majors: string[];
    countries: string[];
    employmentStatuses: string[];
    degreeLevels: string[];
    connectionTypes: string[];
}

