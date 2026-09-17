"use client";

import { type Ref } from "react";
import { Input } from "@/components/ui/input";

/**
 * The Email and Temporary password pair every Manager-created login is made from — Tester
 * (`CreateTesterDialog`) and Agent (`CreateAgentDialog`, `CreateAgentLoginDialog`) alike. One
 * component, so the fields' names, input types, autocomplete and spellcheck hints can't drift
 * between the dialogs; each dialog still owns its state, placeholder and error mapping.
 */
export function LoginCredentialFields({
  username,
  onUsernameChange,
  password,
  onPasswordChange,
  emailPlaceholder,
  disabled,
  emailInvalid = false,
  passwordInvalid = false,
  autoFocusEmail = false,
  passwordRef,
}: Readonly<{
  username: string;
  onUsernameChange: (value: string) => void;
  password: string;
  onPasswordChange: (value: string) => void;
  emailPlaceholder: string;
  disabled: boolean;
  emailInvalid?: boolean;
  passwordInvalid?: boolean;
  autoFocusEmail?: boolean;
  passwordRef?: Ref<HTMLInputElement>;
}>) {
  return (
    <>
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

      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Temporary password
        <Input
          ref={passwordRef}
          type="password"
          name="password"
          required
          autoComplete="new-password"
          value={password}
          onChange={(event) => onPasswordChange(event.target.value)}
          disabled={disabled}
          invalid={passwordInvalid}
          placeholder="••••••••"
        />
      </label>
    </>
  );
}
