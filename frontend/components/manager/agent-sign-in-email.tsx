"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { CreateAgentLoginDialog } from "@/components/manager/create-agent-login-dialog";

/**
 * The "Sign-in email" field of an Agent's detail view (agent-login-on-creation spec): the email
 * the Agent signs in with, or "No login" with the Create login action.
 *
 * A client component that stays mounted across the refresh after a login is created, because
 * the Create login trigger doesn't: without this, focus would fall back to the document body.
 * Instead focus moves to the new email, and an always-mounted polite status region announces it.
 */
export function AgentSignInEmail({
  agentId,
  agentName,
  loginUsername,
}: Readonly<{
  agentId: string;
  agentName: string;
  loginUsername: string | null;
}>) {
  const router = useRouter();
  const emailRef = useRef<HTMLParagraphElement>(null);
  const [createdUsername, setCreatedUsername] = useState<string | null>(null);
  const email = loginUsername ?? createdUsername;

  useEffect(() => {
    if (createdUsername) emailRef.current?.focus();
  }, [createdUsername]);

  function handleCreated(username: string) {
    setCreatedUsername(username);
    router.refresh();
  }

  return (
    <div className="min-w-0">
      <p className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Sign-in email</p>
      {email ? (
        <p ref={emailRef} tabIndex={-1} className="mt-1 text-sm break-all text-ink">
          {email}
        </p>
      ) : (
        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1.5">
          <p className="text-sm text-ink-mute">No login</p>
          <CreateAgentLoginDialog agentId={agentId} agentName={agentName} onCreated={handleCreated} />
        </div>
      )}
      <output className="sr-only">
        {createdUsername ? `Login created. ${agentName} can now sign in with ${createdUsername}.` : ""}
      </output>
    </div>
  );
}
