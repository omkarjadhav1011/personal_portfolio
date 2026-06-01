
import { InputHTMLAttributes, TextareaHTMLAttributes, SelectHTMLAttributes, ReactNode } from "react";

const inputBase =
  "w-full bg-terminal-bg border rounded-lg px-3 py-2 font-mono text-xs text-text-primary placeholder-text-faint focus:outline-none transition-colors";
const inputNormal = "border-terminal-border focus:border-git-blue/50 focus:ring-1 focus:ring-git-blue/20";
const inputError = "border-git-red/50 focus:border-git-red/70 focus:ring-1 focus:ring-git-red/30";

function inputClass(error?: string) {
  return `${inputBase} ${error ? inputError : inputNormal}`;
}

interface FieldProps {
  label: string;
  error?: string;
  children: ReactNode;
}

function FieldWrapper({ label, error, children }: FieldProps) {
  return (
    <div className="space-y-1">
      <label className="block font-mono text-xs text-text-muted">{label}</label>
      {children}
      {error && <p className="font-mono text-[10px] text-git-red">{error}</p>}
    </div>
  );
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

export function FormInput({ label, error, className, ...props }: InputProps) {
  return (
    <FieldWrapper label={label} error={error}>
      <input className={`${inputClass(error)} ${className ?? ""}`} aria-invalid={!!error} {...props} />
    </FieldWrapper>
  );
}

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  error?: string;
}

export function FormTextarea({ label, error, className, ...props }: TextareaProps) {
  return (
    <FieldWrapper label={label} error={error}>
      <textarea className={`${inputClass(error)} resize-none ${className ?? ""}`} aria-invalid={!!error} {...props} />
    </FieldWrapper>
  );
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string;
  options: { value: string; label: string }[];
}

export function FormSelect({ label, error, options, className, ...props }: SelectProps) {
  return (
    <FieldWrapper label={label} error={error}>
      <select className={`${inputClass(error)} ${className ?? ""}`} aria-invalid={!!error} {...props}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </FieldWrapper>
  );
}

interface CheckboxProps {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}

export function FormCheckbox({ label, checked, onChange }: CheckboxProps) {
  return (
    <label className="flex items-center gap-2 cursor-pointer font-mono text-xs text-text-muted">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="w-3.5 h-3.5 accent-git-green"
      />
      {label}
    </label>
  );
}
