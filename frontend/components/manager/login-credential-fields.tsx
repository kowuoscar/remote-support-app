"use client";

import { Input } from "@/components/ui/input";

export const GENERATED_PASSWORD_HINT = "A password is generated when you create the login — you'll see it once.";

/**
 * The Email field every Manager-created login is made from — Tester (`CreateTesterDialog`) and
 * Agent (`CreateAgentDialog`, `CreateAgentLoginDialog`) alike. One component, so the field's
 * name, input type, autocomplete and spellcheck hints can't drift between the dialogs; each
 * dialog still owns its state, placeholder and error mapping. The password is never typed: the
 * backend generates it and the dialog reveals it once.
 */
export function LoginCredentialFields({
  username,
  onUsernameChange,
  emailPlaceholder,
  disabled,
  emailInvalid = false,
  autoFocusEmail = false,
}: Readonly<{
  username: string;
  onUsernameChange: (value: string) => void;
  emailPlaceholder: string;
  disabled: boolean;
  emailInvalid?: boolean;
  autoFocusEmail?: boolean;
}>) {
  return (
    <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
      Email
      <Input
        type="email"
        name="username"
        autoFocus={autoFocusEmail}
        required
        autoComplete="off"
        spellCheck={false}
        value={username}
        onChange={(event) => onUsernameChange(event.target.value)}
        disabled={disabled}
        invalid={emailInvalid}
        placeholder={emailPlaceholder}
      />
    </label>
  );
}
