import { useState, useCallback } from "react";
import type { ZodSchema, ZodError } from "zod";

export function useFormValidation<T>(schema: ZodSchema<T>) {
  const [errors, setErrors] = useState<Record<string, string>>({});

  const mapErrors = useCallback((zodError: ZodError) => {
    const mapped: Record<string, string> = {};
    for (const issue of zodError.issues) {
      const key = issue.path.join(".");
      if (!mapped[key]) mapped[key] = issue.message;
    }
    return mapped;
  }, []);

  const validate = useCallback(
    (data: unknown): data is T => {
      const result = schema.safeParse(data);
      if (result.success) {
        setErrors({});
        return true;
      }
      setErrors(mapErrors(result.error));
      return false;
    },
    [schema, mapErrors]
  );

  const validateField = useCallback(
    (field: string, value: unknown, formData: Record<string, unknown>) => {
      // Validate the full object to catch cross-field issues, but only show this field's error
      const result = schema.safeParse({ ...formData, [field]: value });
      if (result.success) {
        setErrors((prev) => {
          const next = { ...prev };
          delete next[field];
          return next;
        });
      } else {
        const fieldError = result.error.issues.find(
          (i) => i.path.join(".") === field
        );
        setErrors((prev) => {
          if (fieldError) return { ...prev, [field]: fieldError.message };
          const next = { ...prev };
          delete next[field];
          return next;
        });
      }
    },
    [schema]
  );

  const clearErrors = useCallback(() => setErrors({}), []);

  return { errors, validate, validateField, clearErrors };
}
