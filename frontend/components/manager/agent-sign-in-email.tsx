"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { CreateAgentLoginDialog } from "@/components/manager/create-agent-login-dialog";
import { ResetPasswordDialog, type ResetPasswordDialogHandle } from "@/components/manager/reset-password-dialog";
import { LoginActivationDialog, type LoginActivationDialogHandle } from "@/components/manager/login-activation-dialog";
import { DeactivatedTag, LoginActivationButton } from "@/components/manager/login-activation-controls";
import { useAnnouncement } from "@/components/ui/use-announcement";
import { Button } from "@/components/ui/button";

/**
 * The "Sign-in email" field of an Agent's detail view (agent-login-on-creation spec): the email
 * the Agent signs in with, or "No login" with the Create login action.
 *
 * A client component that stays mounted across the refresh after a login is created, because
 * the Create login trigger doesn't: without this, focus would fall back to the document body.
 * Instead focus moves to the new email, and an always-mounted polite status region announces it.
 * With a login, the **Reset password** row action sits beside the email (manager-resets-a-password),
 * then **Deactivate login** — or, once deactivated, a neutral **Deactivated** tag after the email
 * and **Reactivate login** (deactivate-a-login). That last action is one button whose label and
 * dialog follow the state, so it keeps its place in the row — and its focus — across the refresh.
 * An Agent with a deactivated Login still has its one Login, so Create login is never offered.
 */
export function AgentSignInEmail({
  agentId,
  agentName,
  loginUsername,
  loginDeactivatedAt = null,
}: Readonly<{
  agentId: string;
  agentName: string;
  loginUsername: string | null;
  loginDeactivatedAt?: string | null;
}>) {
  const router = useRouter();
  const emailRef = useRef<HTMLParagraphElement>(null);
  const resetRef = useRef<ResetPasswordDialogHandle>(null);
  const deactivateRef = useRef<LoginActivationDialogHandle>(null);
  const reactivateRef = useRef<LoginActivationDialogHandle>(null);
  const [createdUsername, setCreatedUsername] = useState<string | null>(null);
  const [announcement, announce] = useAnnouncement();
  const email = loginUsername ?? createdUsername;

  useEffect(() => {
    if (createdUsername) emailRef.current?.focus();
  }, [createdUsername]);

  function handleCreated(username: string) {
    setCreatedUsername(username);
    router.refresh();
  }

  const deactivated = loginDeactivatedAt !== null;

  return (
    <div className="min-w-0">
      <p className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Sign-in email</p>
      {email ? (
        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1.5">
          <p ref={emailRef} tabIndex={-1} className="text-sm break-all text-ink">
            {email}
          </p>
          {loginDeactivatedAt ? <DeactivatedTag deactivatedAt={loginDeactivatedAt} /> : null}
          <Button
            variant="row"
            size="sm"
            onClick={() =>
              resetRef.current?.open({
                name: agentName,
                email,
                endpoint: `/api/agents/${agentId}/login/password`,
                listLink: { href: "/manager/agents", label: "Back to the Agents list" },
              })
            }
          >
            Reset password
          </Button>
          <LoginActivationButton
            deactivated={deactivated}
            onClick={() =>
              (deactivated ? reactivateRef : deactivateRef).current?.open({
                name: agentName,
                email,
                endpoint: `/api/agents/${agentId}/login/${deactivated ? "reactivate" : "deactivate"}`,
                listLink: { href: "/manager/agents", label: "Back to the Agents list" },
              })
            }
          />
          <ResetPasswordDialog ref={resetRef} onReset={(resetEmail) => announce(`Password reset for ${resetEmail}.`)} />
          <LoginActivationDialog
            ref={deactivateRef}
            mode="deactivate"
            onChanged={(changedEmail) => announce(`Login deactivated for ${changedEmail}.`)}
          />
          <LoginActivationDialog
            ref={reactivateRef}
            mode="reactivate"
            onChanged={(changedEmail) => announce(`Login reactivated for ${changedEmail}.`)}
          />
        </div>
      ) : (
        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1.5">
          <p className="text-sm text-ink-mute">No login</p>
          <CreateAgentLoginDialog agentId={agentId} agentName={agentName} onCreated={handleCreated} />
        </div>
      )}
      <output className="sr-only">
        {announcement ??
          (createdUsername ? `Login created. ${agentName} can now sign in with ${createdUsername}.` : "")}
      </output>
    </div>
  );
}
