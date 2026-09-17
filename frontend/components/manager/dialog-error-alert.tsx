import Link from "next/link";
import { IconAlertTriangle } from "@/components/icons";

/** The inline 409/400/500 error every Manager create/login dialog shows the same way. */
export function DialogErrorAlert({
  message,
  link,
}: Readonly<{
  message: string;
  link?: { href: string; label: string };
}>) {
  return (
    <div role="alert" className="flex items-start gap-2 rounded-lg bg-danger-bg px-3 py-2.5 text-[13px] text-danger">
      <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
      <span>
        {message}
        {link ? (
          <>
            {" "}
            <Link href={link.href} className="font-medium underline underline-offset-2">
              {link.label}
            </Link>
          </>
        ) : null}
      </span>
    </div>
  );
}
