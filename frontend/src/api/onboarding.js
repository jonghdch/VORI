import { get, post } from "./http";

export const getOnboardingStatus = () => get("/onboarding/status");

export const saveSpendingProfile = (profile) =>
  post("/onboarding/spending-profile", profile);

export const completeOnboarding = () => post("/onboarding/complete");
