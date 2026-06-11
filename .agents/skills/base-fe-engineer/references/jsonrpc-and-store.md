# JSON-RPC And Store Patterns

## JSON-RPC Boundary

Use `lib/jsonv2.ts` as the shared transport layer.

Important conventions from that file:

- `BaseJsonV2Rpc` posts JSON to `"/json/2/<model>/<method>"`.
- The transport uses Axios with a bearer token header.
- JSON-RPC failures are converted into thrown `Error` objects.
- `JsonV2Response<T>` uses `{ code, data?, error? }`.
- Company filtering is injected through `withCompany()` using `sessionStorage.companyIds`.

## Existing RPC Exports

Examples already exported:

- `UserV2Rpc`
- `PartnerV2Rpc`
- `EdcTxnV2Rpc`
- `SettleTxnV2Rpc`
- `PaymentTxnV2Rpc`
- `AccountMoveV2Rpc`

If a needed model is missing:

1. Add `export const XyzV2Rpc = new BaseJsonV2Rpc("<model.name>");`
2. Update `setRpcToken()` so the new client receives the access token.

## Store Pattern

`app/(app)/transactions/transactionStore.ts` shows the preferred shape:

- define a typed store interface
- create the store with `create<StoreType>((set) => ({ ... }))`
- expose focused async actions like `fetchEdcTransaction`
- keep transformation and grouping logic inside store actions when it is shared
- catch and log fetch errors at the action boundary
- avoid unnecessary updates by comparing current and next values before `set()`

## Feature Composition Pattern

`app/(app)/transactions/components/edc/index.tsx` shows a typical composition:

- client component page or feature shell
- table columns declared close to the consumer
- `useEffect` triggers data loads from a Zustand store
- filters and forms use Zod plus `react-hook-form`
- shadcn controls (`Select`, `Popover`, `Button`, `Calendar`, `Form`) compose the UI

## Recommended Default

For new frontend work in this repo:

1. Put reusable backend calls in `lib/jsonv2.ts`.
2. Put shared feature state and async loading in a Zustand store.
3. Put view-specific composition, columns, and local toggles in the route component.
4. Use Zod at the form or boundary layer instead of ad hoc field checks in JSX.
