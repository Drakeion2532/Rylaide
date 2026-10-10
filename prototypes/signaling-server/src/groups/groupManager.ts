// Groups are ephemeral and in-memory for now - a ride group only needs to
// exist for the duration of a ride. No database needed for v1.
// If you later want groups to survive a server restart, swap this Map
// for a real store (Postgres, Redis, etc).

export interface RideGroup {
  code: string;
  name: string;
  createdAt: number;
  memberIds: Set<string>;
}

const groups = new Map<string, RideGroup>();

function generateCode(): string {
  // 6 characters, easy to read aloud over a phone call or across a parking lot.
  // Excludes ambiguous characters (0/O, 1/I) on purpose.
  const chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  let code = "";
  for (let i = 0; i < 6; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}

export function createGroup(name: string): RideGroup {
  let code = generateCode();
  while (groups.has(code)) code = generateCode(); // avoid rare collision

  const group: RideGroup = {
    code,
    name,
    createdAt: Date.now(),
    memberIds: new Set(),
  };
  groups.set(code, group);
  return group;
}

export function getGroup(code: string): RideGroup | undefined {
  return groups.get(code);
}

export function addMember(code: string, riderId: string): RideGroup | undefined {
  const group = groups.get(code);
  if (!group) return undefined;
  group.memberIds.add(riderId);
  return group;
}

export function removeMember(code: string, riderId: string): void {
  const group = groups.get(code);
  if (!group) return;
  group.memberIds.delete(riderId);
  // Clean up empty groups so memory doesn't grow unbounded over time.
  if (group.memberIds.size === 0) groups.delete(code);
}
