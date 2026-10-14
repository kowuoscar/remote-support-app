import { describe, expect, it } from "vitest";
import { forwardJson } from "./forward-json";

describe("forwardJson", () => {
  it("passes status, body and the JSON content type through", async () => {
    const forwarded = await forwardJson(new Response('{"code":"X"}', { status: 400 }));

    expect(forwarded.status).toBe(400);
    expect(forwarded.headers.get("Content-Type")).toBe("application/json");
    expect(await forwarded.text()).toBe('{"code":"X"}');
  });

  it("passes a 204 through with no body and no content type", async () => {
    const forwarded = await forwardJson(new Response(null, { status: 204 }));

    expect(forwarded.status).toBe(204);
    expect(forwarded.headers.get("Content-Type")).toBeNull();
    expect(await forwarded.text()).toBe("");
  });
});
