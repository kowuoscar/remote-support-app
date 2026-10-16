/** A search parameter's first value: a page's `searchParams` give `string | string[]` for a repeated key. */
export function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
