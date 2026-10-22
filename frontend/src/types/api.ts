/**
 * API contracts mirroring the Spring Boot DTOs. Money and percentages arrive as decimal STRINGS
 * ("1234.50") so no precision is lost to JavaScript floating point; the UI only formats them.
 */
export type Decimal = string;
export type ISODate = string; // yyyy-MM-dd
export type YearMonthString = string; // yyyy-MM
export type TransactionType = "INCOME" | "EXPENSE";
export type PaymentMethod = "CASH" | "DEBIT_CARD" | "CREDIT_CARD" | "UPI" | "BANK_TRANSFER" | "WALLET" | "OTHER";
export type Theme = "SYSTEM" | "LIGHT" | "DARK";

export interface User {
  id: string;
  email: string;
  displayName: string;
  defaultCurrency: string;
  locale: string;
  /** IANA zone used by the server for "today" and "this month". */
  timeZone: string;
  theme: Theme;
  budgetAlertsEnabled: boolean;
  budgetAlertThreshold: number;
  createdAt: string;
}

export interface CategoryRef {
  id: string;
  name: string;
  type: TransactionType;
  icon: string;
}

export interface Category extends CategoryRef {
  isDefault: boolean;
  archived: boolean;
  transactionCount: number;
}

export interface CategoryUsage {
  transactionCount: number;
  budgetLineCount: number;
}

export interface Transaction {
  id: string;
  type: TransactionType;
  amount: Decimal;
  currency: string;
  category: CategoryRef;
  description: string;
  transactionDate: ISODate;
  merchant?: string;
  paymentMethod?: PaymentMethod;
  notes?: string;
  sample: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface TransactionInput {
  type: TransactionType;
  amount: string;
  currency: string;
  categoryId: string;
  description: string;
  transactionDate: ISODate;
  merchant?: string | null;
  paymentMethod?: PaymentMethod | null;
  notes?: string | null;
  version?: number;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type BudgetStatus = "NO_LIMIT" | "ON_TRACK" | "WARNING" | "EXCEEDED";

export interface Measure {
  limit?: Decimal;
  spent: Decimal;
  remaining?: Decimal;
  overBy?: Decimal;
  percentUsed?: Decimal;
  barPercent?: Decimal;
  status: BudgetStatus;
}

export interface BudgetLine {
  category: CategoryRef;
  measure: Measure;
}

export interface BudgetMonth {
  month: YearMonthString;
  currency: string;
  planExists: boolean;
  sample: boolean;
  total: Measure;
  lines: BudgetLine[];
  allocated: Decimal;
  unbudgetedSpent: Decimal;
  overAllocated: boolean;
  alertThreshold: number;
  dailyAllowance?: Decimal;
  daysLeft?: number;
}

export interface BudgetHistoryEntry {
  month: YearMonthString;
  totalLimit?: Decimal;
  spent: Decimal;
  percentUsed?: Decimal;
  status: BudgetStatus;
  lineCount: number;
  exceededLines: number;
}

export interface SaveBudgetInput {
  currency: string;
  totalLimit: string | null;
  lines: { categoryId: string; limit: string }[];
}

export interface Goal {
  id: string;
  name: string;
  targetAmount: Decimal;
  currency: string;
  targetDate?: ISODate;
  notes?: string;
  savedAmount: Decimal;
  remainingAmount: Decimal;
  progressPercent: Decimal;
  barPercent: Decimal;
  completed: boolean;
  contributionCount: number;
  lastContributionDate?: ISODate;
  monthlyNeeded?: Decimal;
  daysLeft?: number;
  sample: boolean;
  version: number;
}

export type ContributionType = "DEPOSIT" | "WITHDRAWAL";

export interface Contribution {
  id: string;
  type: ContributionType;
  amount: Decimal;
  date: ISODate;
  note?: string;
  createdAt: string;
}

export interface Totals {
  income: Decimal;
  expenses: Decimal;
  net: Decimal;
  incomeCount: number;
  expenseCount: number;
}

export interface CategorySlice {
  categoryId: string;
  name: string;
  icon: string;
  total: Decimal;
  count: number;
  sharePercent?: Decimal;
  previousTotal: Decimal;
  changePercent?: Decimal;
}

export interface TrendPoint {
  start: ISODate;
  end: ISODate;
  income: Decimal;
  expenses: Decimal;
}

export interface Insight {
  rule: string;
  tone: "POSITIVE" | "NEUTRAL" | "WARNING";
  message: string;
}

export interface Overview {
  currency: string;
  range: { from: ISODate; to: ISODate; days: number };
  previousRange: { from: ISODate; to: ISODate; days: number };
  totals: Totals;
  previousTotals: Totals;
  comparison: { incomeChangePercent?: Decimal; expenseChangePercent?: Decimal; netDelta: Decimal };
  savingsRatePercent?: Decimal;
  averageDailyExpense: Decimal;
  expenseCategories: CategorySlice[];
  incomeCategories: CategorySlice[];
  granularity: "DAY" | "WEEK" | "MONTH";
  trend: TrendPoint[];
  insights: Insight[];
}

export interface MonthPoint {
  month: YearMonthString;
  income: Decimal;
  expenses: Decimal;
  net: Decimal;
  expenseChangePercent?: Decimal;
}

export interface RecurringPattern {
  label: string;
  categoryName: string;
  cadence: "WEEKLY" | "BIWEEKLY" | "MONTHLY";
  typicalAmount: Decimal;
  occurrences: number;
  firstDate: ISODate;
  lastDate: ISODate;
  nextExpectedDate: ISODate;
  monthlyEquivalent: Decimal;
}

export interface Dashboard {
  overview: Overview;
  budget: BudgetMonth;
  recentTransactions: Transaction[];
  goals: Goal[];
  monthly: MonthPoint[];
  hasAnyTransactions: boolean;
  sampleDataPresent: boolean;
  currencies: string[];
}

export interface AppNotification {
  id: string;
  type: "BUDGET_THRESHOLD" | "BUDGET_EXCEEDED";
  title: string;
  message: string;
  link?: string;
  read: boolean;
  createdAt: string;
}

export interface SearchResults {
  query: string;
  transactions: Transaction[];
  transactionTotal: number;
  categories: CategoryRef[];
  goals: { id: string; name: string; currency: string }[];
}

export interface AccountOptions {
  currencies: string[];
  locales: string[];
  timeZones: string[];
}

export interface FieldError {
  field: string;
  message: string;
}
