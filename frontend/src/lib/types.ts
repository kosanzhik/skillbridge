// Mirrors backend DTOs (kz.skillbridge.*.dto)

export type SkillSource = "SELF_REPORTED" | "CV_EXTRACTED" | "ASSESSED" | "VERIFIED";
export type DesiredLevel = "INTERN" | "JUNIOR" | "MIDDLE" | "SENIOR" | "LEAD";
export type WorkFormat = "OFFICE" | "HYBRID" | "REMOTE" | "ANY";

export interface Skill { id: number; slug: string; name: string; category: string }

export interface Profile {
  fullName: string | null; location: string | null; headline: string | null; languages: string[];
  desiredLevel: DesiredLevel | null; workFormat: WorkFormat | null; onboarded: boolean;
}
export interface Me { id: number; email: string; role: "USER" | "ADMIN"; demo: boolean; createdAt: string; profile: Profile }
export interface AuthResponse { token: string; expiresAt: string; user: Me }

export interface UserSkill { skill: Skill; level: number; source: SkillSource; proven: boolean; evidence: string | null; updatedAt: string }

export interface ParsedExperience { company: string | null; title: string; startDate: string | null; endDate: string | null; description: string | null }
export interface ParsedEducation { institution: string; degree: string | null; field: string | null; startYear: number | null; endYear: number | null }
export interface ParsedSkill { name: string; level: number | null; evidence: string | null; skillId: number | null; skillName: string | null }
export interface ParsedResume {
  fullName: string | null; headline: string | null; location: string | null; languages: string[];
  experiences: ParsedExperience[]; educations: ParsedEducation[];
  projects: { name: string; description: string | null; skills: string[] }[];
  certifications: { name: string; issuer: string | null; year: number | null }[];
  skills: ParsedSkill[];
}
export interface Resume {
  id: number; originalFilename: string; sizeBytes: number;
  status: "UPLOADED" | "PARSING" | "PARSED" | "CONFIRMED" | "FAILED";
  parser: "OPENAI" | "KEYWORD" | null; parseError: string | null; createdAt: string; confirmedAt: string | null;
  parsed: ParsedResume | null; unmatchedSkills: string[];
}
export interface Background {
  experiences: { id: number; company: string | null; title: string; startDate: string | null; endDate: string | null; description: string | null; source: string }[];
  educations: { id: number; institution: string; degree: string | null; field: string | null; startYear: number | null; endYear: number | null; source: string }[];
}

export interface ProfessionRef { id: number; slug: string; title: string }
export interface ProfessionSummary extends ProfessionRef { description: string | null; requirementCount: number }
export interface ProfessionDetail extends ProfessionRef {
  description: string | null; requirementsSource: string;
  requirements: { skill: Skill; requiredLevel: number; importance: number }[];
}
export interface CareerGoal { id: number; profession: ProfessionRef; primary: boolean; createdAt: string }

export interface JobSummary {
  id: number; source: "HH" | "TELEGRAM"; title: string; company: string | null; location: string | null;
  salaryFrom: number | null; salaryTo: number | null; salaryCurrency: string | null; salaryGross: boolean | null;
  experience: string | null; schedule: string | null; url: string; publishedAt: string | null;
}
export interface SkillMatch {
  skillId: number; skill: string; requirement: "REQUIRED" | "PREFERRED"; weight: number; requiredLevel: number;
  userLevel: number; userSource: SkillSource | null; proven: boolean; credit: number;
}
export interface JobMatch { job: JobSummary; score: number | null; matched: SkillMatch[]; partial: SkillMatch[]; missing: SkillMatch[] }
export interface MatchesResponse { profession: ProfessionRef; jobsAnalyzed: number; methodology: string; disclaimer: string; matches: JobMatch[] }
export interface JobDetail { job: JobSummary; employment: string | null; description: string | null; requirements: { skill: Skill; type: "REQUIRED" | "PREFERRED" }[] }

export type GapStatus = "STRONG" | "NEEDS_IMPROVEMENT" | "BIG_GAP";
export interface SkillGapItem {
  skill: Skill; currentLevel: number; currentSource: SkillSource | null; proven: boolean; requiredLevel: number; gap: number;
  status: GapStatus; importance: number; importanceSource: "MARKET" | "BASELINE"; demandShare: number | null;
  jobsMentioning: number; priority: number;
}
export interface Readiness { score: number; provenScore: number; explanation: string }
export interface SkillGapResponse {
  profession: ProfessionRef; jobsAnalyzed: number; requirementsSource: "MARKET" | "BASELINE";
  readiness: Readiness; methodology: string; skills: SkillGapItem[];
}

export interface LearningResource {
  id: number; skill: string; title: string; provider: string; url: string; type: string;
  level: number | null; language: string | null; free: boolean | null; verifiedAt: string | null;
}
export type ChallengeType = "QUIZ" | "SQL" | "OPEN_TEXT";
export interface ChallengeRef { id: number; title: string; type: ChallengeType; level: number }
export type ItemStatus = "TODO" | "IN_PROGRESS" | "DONE";
export interface RoadmapItem {
  id: number; position: number; skill: Skill | null; title: string; goal: string | null; topics: string[];
  estimatedHours: number | null; completionCriteria: string | null; rationale: string | null; status: ItemStatus;
  completedAt: string | null; resources: LearningResource[]; challenges: ChallengeRef[];
}
export interface RoadmapProgress { total: number; done: number; inProgress: number; percent: number }
export interface Roadmap {
  id: number; profession: ProfessionRef; status: string; createdAt: string; progress: RoadmapProgress; note: string;
  items: RoadmapItem[];
}

export interface Challenge {
  id: number; skill: Skill; title: string; description: string; type: ChallengeType; level: number; passScore: number;
  questions: { id: string; text: string; options: string[] }[];
  criteria: { name: string; description: string; maxPoints: number }[];
}
export interface SubmissionResult {
  submissionId: number; challengeId: number; challengeTitle: string; status: "PASSED" | "FAILED" | "PENDING";
  score: number | null; passScore: number; assessedBy: "AUTO" | "AI" | null; feedback: string | null;
  details: Record<string, unknown> | null;
  skillUpdate: { skill: string; previousAssessedLevel: number | null; newAssessedLevel: number } | null;
  roadmapItemCompletedId: number | null; submittedAt: string;
}

export interface ProgressResponse {
  roadmap: RoadmapProgress | null; readiness: Readiness | null;
  skills: { total: number; proven: number; cvExtracted: number; selfReported: number };
  nextAction: { type: "UPLOAD_CV" | "CHOOSE_GOAL" | "GENERATE_ROADMAP" | "CONTINUE_ITEM" | "REVIEW_JOBS"; title: string; roadmapItemId: number | null };
  recentEvents: { type: string; payload: Record<string, unknown>; createdAt: string }[];
}
