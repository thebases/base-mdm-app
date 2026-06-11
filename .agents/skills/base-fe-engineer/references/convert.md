# Convert / Refactor — Old UI → New UI

Use this reference whenever the task involves keywords like **convert**, **refactor**, **migrate UI**, **new design**, or **redesign** a page or component.

## Core Rule

**Change the UI shell only. Keep all data logic untouched.**

The old code's `useEffect` calls, Zustand store actions, JSON-RPC calls, domain filters, and pagination math are correct and battle-tested. Your job is to swap the visual layer while wiring it to those same data sources. Never rewrite what the data layer does just because you are replacing how it looks.

---

## Mental Model: Two Layers, One Touch

```text
┌──────────────────────────────┐
│  New UI Shell (CHANGE THIS)  │  ← new components, new layout, new tokens
│  TabNavigation, FilterBar,   │
│  DataTable, MetricCard, etc. │
├──────────────────────────────┤
│  Data Layer (DO NOT TOUCH)   │  ← useEffect, Zustand store, JSON-RPC calls
│  useXxxStore(), fetchXxx(),  │
│  domain filters, sort, page  │
└──────────────────────────────┘
```

If the old component had a `useEffect` that calls `fetchInvoices(merchantId)`, keep that call verbatim. If it had `useTransactionStore()` destructured to get `edcSummary`, keep that. Route the state values and callbacks into the new UI props.

---

## Step-by-Step Workflow

1. **Read the old file first.** Identify every `useEffect`, store selector, RPC call, and event handler. List them — these are sacred.
2. **Read the new UI reference** in `.agents/references/newui/src/` for the visual components that match the page type (table, metric cards, filter bar, modals, tabs).
3. **Map old state → new component props.** For each piece of state the old component had, find which new UI prop receives it.
4. **Write the new file.** Keep all imports from Zustand stores, keep all `useEffect` bodies, replace only JSX and shadcn primitives with the new UI components.
5. **Verify.** Run `node_modules/.bin/tsc --noEmit` on the changed files. Fix type errors without changing data flow.

---

## New UI Component Reference

All reference components live in `.agents/references/newui/src/app/components/` and `.agents/references/newui/src/app/pages/`.

### Layout components

| Component        | File                               | Use for                                          |
| ---------------- | ---------------------------------- | ------------------------------------------------ |
| `PageHeader`     | `components/PageHeader.tsx`        | Top bar with title, notifications, user avatar   |
| `RootLayout`     | `components/RootLayout.tsx`        | Sidebar + content shell                          |
| `MainNavigation` | `components/MainNavigation.tsx`    | Left sidebar nav items                           |
| `TopNavigation`  | `components/TopNavigation.tsx`     | Secondary top nav                                |

### Page content components

| Component           | File                                  | Use for                                             |
| ------------------- | ------------------------------------- | --------------------------------------------------- |
| `TabNavigation`     | `components/TabNavigation.tsx`        | QR / Card / Summary style tab bar                   |
| `FilterBar`         | `components/FilterBar.tsx`            | Search + date range + dropdown filters row          |
| `DataTable`         | `components/DataTable.tsx`            | Data table with header actions, export, pagination  |
| `MetricCard`        | `components/MetricCard.tsx`           | Summary stat card (label + value + icon + trend)    |
| `MetricCardsGrid`   | `components/MetricCard.tsx`           | Responsive grid wrapper for metric cards            |
| `MetricCardDetailed`| `components/MetricCardDetailed.tsx`   | Metric card with amount + txn count + % change      |
| `Pagination`        | `components/Pagination.tsx`           | Page controls (currentPage, totalPages, pageSize)   |
| `SearchableSelect`  | `components/SearchableSelect.tsx`     | Searchable dropdown                                 |
| `DateRangePicker`   | `components/DateRangePicker.tsx`      | Date range input                                    |

### Modal components

| Component            | File                                  | Use for                        |
| -------------------- | ------------------------------------- | ------------------------------ |
| `BankAccountModal`   | `components/BankAccountModal.tsx`     | Add / edit bank account form   |
| `VirtualAccountModal`| `components/VirtualAccountModal.tsx`  | Virtual account creation       |
| `CreateQRModal`      | `components/CreateQRModal.tsx`        | QR code generation             |
| `ConfirmDialog`      | `components/ConfirmDialog.tsx`        | Generic yes/no confirmation    |
| `BulkUploadModal`    | `components/BulkUploadModal.tsx`      | CSV / bulk upload flow         |

### Reference page examples

| Page                | File                            | Shows                                                       |
| ------------------- | ------------------------------- | ----------------------------------------------------------- |
| `TransactionsPage`  | `pages/TransactionsPage.tsx`    | Tabs + FilterBar + MetricCards + DataTable wired together   |
| `BankAccountsPage`  | `pages/BankAccountsPage.tsx`    | Tab switch + filter + card list + modals                    |
| `StoresPage`        | `pages/StoresPage.tsx`          | Filter + table + add modal                                  |
| `EmployeesPage`     | `pages/EmployeesPage.tsx`       | Table + role-based visibility                               |

---

## Wiring Pattern: Old State → New Props

### Tabs

Old code often used a `useState` for active tab already. Keep it, route to `TabNavigation`:

```tsx
// OLD (keep this)
const [activeTab, setActiveTab] = useState('qr')

// NEW shell (just the JSX changes)
<TabNavigation
  tabs={[{ id: 'qr', label: 'QR' }, { id: 'card', label: 'Thẻ' }]}
  activeTab={activeTab}
  onTabChange={setActiveTab}
/>
```

### Filters / Search

Old code had individual `useState` for dates, search, etc. Keep every `useState`. Route into `FilterBar` props:

```tsx
// OLD (keep all of this)
const [searchValue, setSearchValue] = useState('')
const [startDate, setStartDate] = useState('')
const [endDate, setEndDate] = useState('')

// NEW shell
<FilterBar
  searchValue={searchValue}
  onSearchChange={setSearchValue}
  startDate={startDate}
  endDate={endDate}
  onDateRangeChange={(s, e) => { setStartDate(s); setEndDate(e) }}
/>
```

### Data fetch (useEffect)

Copy the old `useEffect` body verbatim. The dependency array must stay correct:

```tsx
// OLD (copy exactly — do not simplify or merge)
useEffect(() => {
  const merchantId = auth.merchantData?.id
  if (!merchantId || !canViewInvoices) return
  void fetchInvoices(merchantId)
}, [auth.merchantData?.id, canViewInvoices, fetchInvoices])
```

### Summary / Metric cards

Old code typically mapped store data into summary arrays. Keep the mapping, pass to `MetricCard`:

```tsx
// OLD (keep the store selector)
const { summaryCard, edcSummary } = useXxxStore()

// NEW shell
<MetricCardsGrid columns={3}>
  {summaryCard.map(card => (
    <MetricCard key={card.label} label={card.label} value={card.value} icon={card.icon} />
  ))}
</MetricCardsGrid>
```

### Tables

Old code had `ColumnDef[]` from `@tanstack/react-table`. For pages using the new `DataTable`, translate column definitions into the new `{ key, label, render?, align?, width? }` shape, but keep all value transformers from the old `cell` renderers:

```tsx
// OLD tanstack column (keep the cell logic)
{ accessorKey: 'amount', cell: ({ row }) => <span className="text-green-700">{formatCurrency(row.getValue('amount'))}</span> }

// NEW DataTable column (same render logic, new shape)
{ key: 'amount', label: t('txn.amount'), render: (row) => <span className="font-semibold text-green-700">{formatCurrency(row.amount)}</span> }
```

If the existing page already uses `BaseTable` from `@/components/base-ui/table/BaseTable`, prefer to keep using it and only replace outer layout / header / filter UI. Only migrate to the reference `DataTable` when the whole page is being redesigned.

### Pagination

Old pages often used `offset + limit` pagination driven by the store. Keep that. Wire page change into the store action call, not into local filtering:

```tsx
// OLD (keep — store drives the backend call)
const { data, totalCount, fetchPage } = useXxxStore()
const handlePageChange = (page: number) => { void fetchPage(page, pageSize) }

// NEW shell
<DataTable
  data={data}
  currentPage={currentPage}
  totalItems={totalCount}
  pageSize={pageSize}
  onPageChange={handlePageChange}   // calls the store, not local slice
  onPageSizeChange={(size) => { setPageSize(size); setCurrentPage(1) }}
/>
```

---

## What NOT to Change

- `useEffect` dependency arrays — never slim them down to "fix" linting during a UI refactor.
- Store action signatures — if `fetchEdcTransactions({ startDate, endDate, sub_partner_id, zidGroup })` is the call shape, keep every field.
- Permission gates — if old code has `auth.permission?.mp_view_invoice?.r`, keep the exact check.
- JSON-RPC model names and method helpers — do not swap `PartnerV2Rpc.write([id], { vals })` for anything else.
- `i18n` keys — keep `t('invoice.label_invoice_id')` calls; do not inline Vietnamese strings just because the reference design has them hardcoded.

---

## Visual Style Rules (new UI)

Derived from the reference components. Apply these when composing new JSX:

- **Background**: `bg-white` cards with `border border-gray-200 rounded-lg`
- **Text scale**: labels `text-xs`, values `text-2xl font-bold`, table cells `text-xs text-gray-700`
- **Table headers**: `text-[11px] font-semibold text-gray-600 uppercase tracking-wide bg-gray-50`
- **Table rows**: `hover:bg-yellow-50/30` hover tint
- **Active tab indicator**: `border-b-2 border-yellow-500 text-gray-900`
- **Primary action buttons**: `bg-yellow-500 hover:bg-yellow-600 text-white px-3 py-1.5 rounded-md text-xs font-medium`
- **Export button**: `bg-green-600 hover:bg-green-700 text-white`
- **Status badges**: inline `px-2 py-0.5 rounded text-[10px] font-semibold` with color pair per status
- **Amount — positive**: `font-semibold text-green-700`; negative/void: `font-semibold text-red-700`
- **Icons**: Lucide icons, `size-5` in cards, `size-3.5` in buttons, `size-4` in inputs
- **Filter bar**: `bg-white border border-gray-200 rounded-lg p-4 mb-5`, items in a flex row with `gap-3`

---

## Common Pitfalls

| Pitfall | Correct approach |
| --- | --- |
| Rewriting `useEffect` or removing the guard condition | Copy old body verbatim |
| Replacing Zustand store with local `useState` to avoid imports | Keep the store selector |
| Using `fetch()` or `axios` directly instead of the RPC helper | Always go through `XxxV2Rpc.*` |
| Inlining Vietnamese strings instead of `t()` keys | Keep `useTranslation()` and all `t('...')` calls |
| Switching from server pagination to client-side slice | Keep the backend page fetch intact |
| Adding a `useEffect` that wasn't in the old code | Only add if strictly required for new UI behavior |
