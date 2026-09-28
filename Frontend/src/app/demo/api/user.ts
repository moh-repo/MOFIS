import { Role } from "./role";
import { UmsUser } from "./ums";

/**
 * The shape the shell works in: guards and the topbar read this. It is a view of
 * the UMS user, not a wire format — there is no longer a `MOHECORE.Users` table
 * behind it, and since the storage pass it is not persisted either: `AuthService`
 * projects it from `UmsAuthService.profile$` and it lives only as long as the page.
 */
export class User {
  id : number;
  userName : string;
  lastName : string;
  role : Role;

  constructor(){
    this.id = 0;
    this.userName = "";
    this.lastName = "";
    this.role = new Role();
  }
}

/**
 * Single place that derives the shell's `User` from a UMS user, so login and
 * the profile page cannot drift into disagreeing about someone's role.
 */
export function userFromUmsUser(umsUser: UmsUser): User {
  const role = new Role();
  role.roleName = umsUser.isSuperAdmin ? 'SUPER_ADMIN' : umsUser.isAdmin ? 'ADMIN' : 'USER';
  return {
    id: umsUser.id,
    userName: umsUser.userName,
    lastName: umsUser.lastName ?? '',
    role
  };
}
